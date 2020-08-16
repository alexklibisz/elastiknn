package com.klibisz.elastiknn.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.settings.RoutingSettings
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

object Server {

  case class Acknowledged(acknowledged: Boolean = true)
  object Acknowledged {
    implicit val encoder: Encoder[Acknowledged] = deriveEncoder
  }

  private val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: Exception =>
      extractUri { uri =>
        System.err.println(s"Request to [$uri] failed with error [${ex.getLocalizedMessage}]")
        complete(HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(ex.getLocalizedMessage)))
      }
  }

  def elasticRoutes(es: FakeElasticsearch): Route = {
    import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
    handleExceptions(exceptionHandler) {
      concat(
        pathPrefix(Segment) { index: String =>
          concat(
            (pathEnd & post) {
              complete(es.createIndex(index).map(_ => Acknowledged()))
            },
            (path("_forcemerge") & post) {
              complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"POST $index/_forcemerge")))
            },
            path("_mapping") {
              (pathEnd & put) {
                complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"PUT $index/_mapping")))
              }
            },
            path("_search") {
              (pathEnd & post) {
                complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"POST $index/_search")))
              }
            },
            (pathEnd & delete) {
              complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"DELETE $index")))
            }
          )
        },
        (path("_bulk") & post) {
          complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"POST _bulk")))
        },
        (path("_all") & delete) {
          complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"DELETE _all")))
        }
      )
    }
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("elastiknn-server")
    val server = Http().newServerAt("localhost", 8080)
    server.bind(elasticRoutes(new FakeElasticsearch))
  }

}
