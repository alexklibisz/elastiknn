package com.klibisz.elastiknn.server

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.DebuggingDirectives.logRequestResult
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, FromRequestUnmarshaller}
import akka.stream.Materializer
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import com.klibisz.elastiknn.api.{ElasticsearchCodec, Mapping}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.Failure

object Server {

  private object CirceMarshaling extends FailFastCirceSupport {
    override def mediaTypes: immutable.Seq[MediaType.WithFixedCharset] = List(
      MediaType.applicationWithFixedCharset("json; charset=UTF-8", HttpCharsets.`UTF-8`),
      MediaTypes.`application/json`
    )
  }

  private implicit def entityToRequestUnmarshaller[T](feu: FromEntityUnmarshaller[T]): FromRequestUnmarshaller[T] =
    new FromRequestUnmarshaller[T] {
      override def apply(r: HttpRequest)(implicit ec: ExecutionContext, materializer: Materializer): Future[T] = feu(r.entity)
    }

  private val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: Exception =>
      extractUri { uri =>
        System.err.println(s"Request to [$uri] failed with error [${ex.getLocalizedMessage}]")
        complete(HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(ex.getLocalizedMessage)))
      }
  }

  private def healthJson(es: FakeElasticsearch): Json = io.circe.parser.parse(s"""
      |{
      |  "cluster_name" : "elastiknn-server",
      |  "status" : "green",
      |  "timed_out" : false,
      |  "number_of_nodes" : 1,
      |  "number_of_data_nodes" : 1,
      |  "active_primary_shards" : ${es.numIndices()},
      |  "active_shards" : ${es.numIndices()},
      |  "relocating_shards" : 0,
      |  "initializing_shards" : 0,
      |  "unassigned_shards" : 0,
      |  "delayed_unassigned_shards" : 0,
      |  "number_of_pending_tasks" : 0,
      |  "number_of_in_flight_fetch" : 0,
      |  "task_max_waiting_in_queue_millis" : 0,
      |  "active_shards_percent_as_number" : 100.0
      |}
      |""".stripMargin).toTry.get

  def elasticRoutes(es: FakeElasticsearch, logLevel: Logging.LogLevel): Route = {
    import CirceMarshaling._
    logRequestResult("elastiknn-server", logLevel) {
      handleExceptions(exceptionHandler) {
        concat(
          (pathPrefix("_cluster" / "health") & get) {
            complete(healthJson(es))
          },
          (path("_bulk") & post) {
            extractLog { log =>
              entity(new BulkIndexUnmarshaller(log)) { indexRequests =>
//                es.bulkIndex(indexRequests).map(_ => )
                complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"POST _bulk")))
              }
            }
          },
          (path("_all") & delete) {
            complete(es.deleteAll().map(_ => Acknowledged(true)))
          },
          pathPrefix(Segment) { index: String =>
            concat(
              (pathEnd & put) {
                complete(es.createIndex(index).map(_ => CreateIndexResponse(true, true, index)))
              },
              (path("_forcemerge") & post) {
                complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"POST $index/_forcemerge")))
              },
              (path("_mapping") & put) {
                entity(as[Json]) { json =>
                  val mappingOpt = for {
                    props <- json.findAllByKey("properties").headOption
                    vec <- props.findAllByKey("vec").headOption
                    mapping <- ElasticsearchCodec.decode[Mapping](vec.hcursor).toOption
                  } yield mapping
                  val putTry = mappingOpt match {
                    case None          => Failure(new IllegalArgumentException(s"Expected a valid mapping for the `vec` property"))
                    case Some(mapping) => es.putMapping(index, mapping)
                  }
                  complete(putTry.map(_ => Acknowledged(true)))
                }
              },
              path("_search") {
                (pathEnd & post) {
                  complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"POST $index/_search")))
                }
              },
              (pathEnd & delete) {
                val indexes = index.split(",")
                complete(es.deleteIndex(indexes).map(_ => Acknowledged(true)))
              }
            )
          }
        )
      }
    }
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("elastiknn-server")
    val server = Http().newServerAt("localhost", 8080)
    val routes = elasticRoutes(new FakeElasticsearch, Logging.InfoLevel)
    server.bind(routes)
  }

}
