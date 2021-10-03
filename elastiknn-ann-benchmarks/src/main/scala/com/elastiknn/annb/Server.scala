package com.elastiknn.annb

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.klibisz.elastiknn.api.Vec
import io.circe.{Json, JsonObject}
import scopt.OptionParser

import java.nio.file.Path
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.{ClassTag, classTag}
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
    final case class EmptyResponse()
    final case class ErrorResponse(error: String)
  }

  def routes[V <: Vec.KnownDims: ClassTag](
      datasetStore: DatasetStore[V],
      algorithm: LuceneAlgorithm[V],
      luceneStore: LuceneStore,
      parallelism: Int
  ): Route = {
    import Contracts._
    import io.circe.generic.auto._
    import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

    val datasetClazz: Class[_] = classTag[Dataset[V]].runtimeClass
    var dataset: Try[Dataset[V]] = Failure(Uninitialized)

    extractMaterializer { implicit mat: Materializer =>
      implicit val ec: ExecutionContext = mat.executionContext
      val log = mat.system.log
      post {
        path("init") {
          entity(as[EmptyRequest]) { _: EmptyRequest => complete(StatusCodes.OK, EmptyResponse()) }
        } ~
          path("load_index") {
            entity(as[LoadIndexRequest]) {
              case LoadIndexRequest(dataset) =>
                val indexExists = luceneStore.indexPath.resolve(dataset).toFile.exists()
                complete(StatusCodes.OK, LoadIndexResponse(indexExists))
            }
          } ~
          path("fit") {
            entity(as[FitRequest]) {
              case FitRequest(datasetName) =>
                Dataset.All.find(_.name == datasetName) match {
                  // Pattern matching with generics requires some ceremony to appease the compiler.
                  case Some(ds: Dataset[V @unchecked]) if datasetClazz.isInstance(ds) =>
                    dataset = Success(ds)
                    val logInterval = ds.count / 100
                    val readParallelism = parallelism * 2
                    val indexing = datasetStore
                      .indexVectors(readParallelism, ds)
                      .take(100000)
                      .zipWithIndex
                      .map {
                        case (vec, i) =>
                          if (i % logInterval == 0 && i > 0) log.info(s"Indexing ${i / logInterval}% complete")
                          algorithm.toDocument(i + 1, vec)
                      }
                      .runWith(luceneStore.index(parallelism))
                    onComplete(indexing) {
                      case Success(_)  => complete(StatusCodes.OK, EmptyResponse())
                      case Failure(ex) => complete(StatusCodes.InternalServerError, ErrorResponse(ex.getMessage))
                    }
                  case Some(_) => complete(StatusCodes.BadRequest, ErrorResponse(s"Incompatible dataset: $datasetName"))
                  case None    => complete(StatusCodes.BadRequest, ErrorResponse(s"Unknown dataset: $datasetName"))
                }
            }
          } ~
          path("set_query_arguments") {
            complete(StatusCodes.OK, EmptyResponse())
          } ~
          path("query") {
            complete(StatusCodes.OK, EmptyResponse())
          } ~
          path("range_query") {
            complete(StatusCodes.OK, EmptyResponse())
          } ~
          path("get_results") {
            complete(StatusCodes.OK, EmptyResponse())
          } ~
          path("get_range_results") {
            complete(StatusCodes.OK, EmptyResponse())
          } ~
          path("get_additional") {
            complete(StatusCodes.OK, EmptyResponse())
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
      port: Int,
      parallelism: Int
  ) {
    override def toString: String =
      s"Opts(datasetsPath=$datasetsPath, indexPath=$indexPath, vectorType=${vectorType}, algo=${algorithm}, buildArgs=${buildArgs.noSpacesSortKeys}, port=${port})"
  }

  object ServerOpts {
    val Default: ServerOpts =
      ServerOpts(Path.of("/tmp/data"), Path.of("/tmp/index"), "float32", "l2lsh", Json.Null, 8080, 1)
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
          parallelism
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
