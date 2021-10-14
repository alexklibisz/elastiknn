package com.elastiknn.annb

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import com.klibisz.elastiknn.api.Vec
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.{Decoder, Json, JsonObject}
import scopt.OptionParser

import java.nio.file.Path
import scala.concurrent.duration.{Duration, DurationInt, DurationLong}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.{ClassTag, classTag}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object Server {

  object Uninitialized extends IllegalStateException("A member has not been initialized.")

  object Contracts {
    case class EmptyRequest()
    final case class LoadIndexRequest(dataset: String)
    final case class SetQueryArgumentsRequest(query_args: Json)
    final case class LoadIndexResponse(load_index: Boolean)
    final case class FitRequest(dataset: String)
    final case class FitResponse(dataset: String)
    final case class QueryRequest[V <: Vec](X: List[V], k: Int)
    final case class GetResultsResponse(get_results: Seq[Seq[Int]])
    final case class GetAdditionalResponse(get_additional: Json)
    final case class EmptyResponse()
    final case class ErrorResponse(error: String)
    object ErrorResponse {
      def apply(ex: Exception): ErrorResponse = ErrorResponse(ex.getMessage)
    }
  }

  def routes[V <: Vec: ClassTag: Decoder](
      datasetStore: DatasetStore[V],
      algorithm: LuceneAlgorithm[V],
      luceneStore: LuceneStore,
      parallelism: Int,
      count: Int
  ): Route = {
    import Contracts._

    val datasetClazz: Class[_] = classTag[Dataset[V]].runtimeClass
    var dataset: Try[Dataset[V]] = Failure(Uninitialized)
    var queryArgs: Try[Json] = Failure(Uninitialized)
    var results: Try[Seq[Seq[Int]]] = Failure(Uninitialized)

    extractMaterializer { implicit mat: Materializer =>
      implicit val ec: ExecutionContext = mat.executionContext
      val log = mat.system.log
      val exceptionHandler = ExceptionHandler {
        case NonFatal(t) =>
          log.error(t, s"Unexpected exception: ${t.getMessage}")
          complete(StatusCodes.InternalServerError, ErrorResponse(t.getMessage))
      }
      handleExceptions(exceptionHandler) {
        post {
          path("init") {
            entity(as[EmptyRequest]) { _: EmptyRequest => complete(StatusCodes.OK, EmptyResponse()) }
          } ~
            path("load_index") {
              entity(as[LoadIndexRequest]) {
                case LoadIndexRequest(dataset) =>
                  val indexPath = luceneStore.indexPath.resolve(dataset)
                  val indexExists = indexPath.toFile.exists()
                  if (indexExists) log.info(s"Index already exists at $indexPath.")
                  complete(StatusCodes.OK, LoadIndexResponse(indexExists))
              }
            } ~
            path("fit") {
              entity(as[FitRequest]) {
                case FitRequest(datasetName) =>
                  log.info(Dataset.All.find(_.name == datasetName).toString)
                  Dataset.All.find(_.name == datasetName) match {
                    // Pattern matching with type parameters requires some ceremony to appease the compiler.
                    case Some(ds: Dataset[V @unchecked]) if datasetClazz.isInstance(ds) =>
                      dataset = Success(ds)
                      val logInterval = ds.count / 100
                      val readParallelism = parallelism * 2
                      val processParallelism = parallelism
                      val indexParallelism = parallelism
                      val t0 = System.currentTimeMillis()
                      val indexing = datasetStore
                        .indexVectors(readParallelism, ds)
                        .zipWithIndex
                        .mapAsync(processParallelism) {
                          case (vec, i) =>
                            Future {
                              if (i % logInterval == 0 && i > 0) {
                                val rate = i / (System.currentTimeMillis() - t0).millis.toSeconds.max(1)
                                log.info(s"Indexing: ${i / logInterval}% at $rate vps.")
                              }
                              algorithm.toDocument(i, vec)
                            }
                        }
                        .runWith(luceneStore.index(indexParallelism))
                      onComplete(indexing) {
                        case Success(_) =>
                          log.info(s"Indexing completed in ${(System.currentTimeMillis() - t0).millis.toMinutes} minutes.")
                          complete(StatusCodes.OK, EmptyResponse())
                        case Failure(ex) => failWith(ex)
                      }
                    case Some(_) => complete(StatusCodes.BadRequest, ErrorResponse(s"Incompatible dataset: $datasetName"))
                    case None    => complete(StatusCodes.BadRequest, ErrorResponse(s"Unknown dataset: $datasetName"))
                  }
              }
            } ~
            path("set_query_arguments") {
              entity(as[SetQueryArgumentsRequest]) {
                case SetQueryArgumentsRequest(query_args) =>
                  queryArgs = Success(query_args)
                  complete(StatusCodes.OK, EmptyResponse())
              }
            } ~
            path("query") {
              entity(as[QueryRequest[V]]) {
                case QueryRequest(vecs, k) =>
                  val logInterval = vecs.length / 100
                  log.info(s"Logging every $logInterval queries")
                  val querying = for {
                    qargs <- Future.fromTry(queryArgs)
                    searchFunction <- Future.fromTry(algorithm.buildSearchFunction(k, qargs, luceneStore.reader(), mat.executionContext))
                    t0 = System.currentTimeMillis()
                    luceneResults <- Source(vecs).zipWithIndex
                      .map {
                        case (vec, i) =>
                          val res = searchFunction(vec)
                          if (i % logInterval == 0 && i > 0) {
                            val rate = i / (System.currentTimeMillis() - t0).millis.toSeconds.max(1)
                            log.info(s"Searching: ${i / logInterval}% at $rate qps.")
                          }
                          if (res.hits < count) {
                            log.warning(s"Search $i found only ${res.hits} hits.")
                          }
                          res
                      }
                      .runWith(Sink.seq)
                    _ = log.info(s"Searches completed in ${(System.currentTimeMillis() - t0).millis.toMinutes} minutes.")
                  } yield results = Success(luceneResults.toList.map(_.neighbors.toList))
                  onComplete(querying) {
                    case Success(_)  => complete(StatusCodes.OK, EmptyResponse())
                    case Failure(ex) => failWith(ex)
                  }
              }
            } ~
            path("get_results") {
              results match {
                case Success(value) => complete(StatusCodes.OK, GetResultsResponse(value))
                case Failure(ex)    => failWith(ex)
              }
            } ~
            path("range_query") {
              entity(as[Json]) { _ => complete(StatusCodes.BadRequest, ErrorResponse("Not implemented")) }
            } ~
            path("get_range_results") {
              complete(StatusCodes.BadRequest, ErrorResponse("Not implemented"))
            } ~
            path("get_additional") {
              complete(StatusCodes.OK, GetAdditionalResponse(Json.fromJsonObject(JsonObject.empty)))
            }
        }
      }
    }
  }

  final case class ServerOpts(
      datasetsPath: Path,
      indexPath: Path,
      vectorType: String,
      algorithm: String,
      buildArgs: Json,
      count: Int,
      port: Int,
      parallelism: Int
  ) {
    override def toString: String =
      s"Opts(datasetsPath=$datasetsPath, indexPath=$indexPath, vectorType=${vectorType}, algo=${algorithm}, buildArgs=${buildArgs.noSpacesSortKeys}, port=${port})"
  }

  object ServerOpts {
    val Default: ServerOpts =
      ServerOpts(Path.of("/tmp/data"), Path.of("/tmp/index"), "float32", "l2lsh", Json.Null, 8080, 100, 1)
  }

  private val optsParser = new OptionParser[ServerOpts]("server") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[String]("datasets-path")
      .required()
      .action((s, c) => c.copy(datasetsPath = Path.of(s)))
    opt[String]("index-path")
      .required()
      .action((s, c) => c.copy(indexPath = Path.of(s)))
    opt[String]("algorithm")
      .required()
      .action((s, c) => c.copy(algorithm = s))
    opt[String]("vector-type")
      .required()
      .action((s, c) => c.copy(vectorType = s))
    opt[String]("index-args")
      .validate(s =>
        io.circe.parser.parse(s) match {
          case Left(err) => Left(s"Invalid json [$s]. Parsing failed with error [${err.message}]")
          case Right(_)  => Right(())
        }
      )
      .action((s, c) => c.copy(buildArgs = io.circe.parser.parse(s).fold(throw _, identity)))
    opt[Int]("count")
      .required()
      .action((i, c) => c.copy(count = i))
    opt[Int]("port")
      .required()
      .action((i, c) => c.copy(port = i))
    opt[Int]("parallelism")
      .required()
      .action((i, c) => c.copy(parallelism = i))
  }

  def runServer(serverOpts: ServerOpts): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "my-system")
    implicit val ec: ExecutionContext = system.executionContext
    import serverOpts._
    val buildDecoder = io.circe.Decoder[List[Int]]
    val routes = (algorithm, vectorType, buildDecoder.decodeJson(buildArgs)) match {
      case ("l2lsh", "float32", Right(List(dims, l, k, w))) =>
        this.routes(
          new DatasetStore.BigAnnBenchmarksDenseFloat(datasetsPath),
          new LuceneAlgorithm.ElastiknnL2Lsh(dims, l, k, w),
          new LuceneStore.Default(indexPath.resolve(s"${algorithm.name}-${vectorType.name}-$dims-$l-$k-$w")),
          parallelism = parallelism,
          count = count
        )
    }
    val binding = Http().newServerAt("0.0.0.0", port).bind(routes)
    val serverRunning = for {
      b <- binding
      _ = b.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds)
      _ = system.log.info(s"Server running on 0.0.0.0:$port")
      _ <- Future.never
    } yield ()
    Await.result(serverRunning, Duration.Inf)
  }

  def main(args: Array[String]): Unit = optsParser.parse(args, ServerOpts.Default).foreach(runServer)

}
