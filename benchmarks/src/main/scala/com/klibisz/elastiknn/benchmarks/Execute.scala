package com.klibisz.elastiknn.benchmarks

import java.util.Base64

import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.benchmarks.codecs._
import io.circe.parser._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console._
import zio.duration.Duration
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger

import scala.util.Try

object Execute extends App {

  private case class Params(experimentJsonBase64: String = "", datasetsBucket: String = "", datasetsPrefix: String = "", resultsBucket: String = "", resultsPrefix: String = "")
  private case class QueryResult(neighborIds: Seq[String], duration: Duration)

  private val parser = new scopt.OptionParser[Params]("Execute benchmark jobs") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[String]("experimentJsonBase64")
      .text("Experiment case class serialized as a Json string")
      .action((s, c) => c.copy(experimentJsonBase64 = s))
      .required()
  }

  private val decoder = Base64.getDecoder

  private def decodeExperiment(jsonBase64: String): IO[Throwable, Experiment] =
    for {
      jsonString <- ZIO.fromTry(Try(new String(decoder.decode(jsonBase64))))
      experiment <- ZIO.fromEither(decode[Experiment](jsonString))
    } yield experiment

  /*

  Logic:
  - Get experiment with a single mapping and many queries.
  - Use s3 results client to check if results exist for _all_ of the queries. If so, we're done.
  - Otherwise proceed.
  - Create index with given mapping.
  - Stream dataset from S3 and index it.
  - Run exact search to get ground truth results.
  - Run all of the queries which don't yet have results.

   */

  private def indexAndSearch(dataset: Dataset,
                             mapping: Mapping,
                             query: NearestNeighborsQuery,
                             k: Int): ZIO[Console with Clock with Logging with DatasetClient with ElastiknnZioClient, Throwable, Result] = {

    // Map the (dataset, mapping)) to an index name and check if this index already exists. If it does, just run the query.
    ZIO.succeed(Result(dataset, mapping, query, k, Seq.empty))
  }

  private def setRecalls(exact: Result, test: Result): Result = {
    val withRecalls = exact.singleResults.zip(test.singleResults).map {
      case (ex, ts) => ts.copy(recall = ex.neighbors.intersect(ts.neighbors).length * 1d / ex.neighbors.length)
    }
    test.copy(singleResults = withRecalls)
  }

  private def run(experiment: Experiment): ZIO[Console with Clock with Logging with DatasetClient with ElastiknnZioClient with ResultClient, Any, Unit] = {
    import experiment._
    for {
      rc <- ZIO.access[ResultClient](_.get)
      testEffects = for {
        Query(testQuery, k) <- experiment.testQueries
      } yield {
        for {
          exactOpt <- rc.find(dataset, exactMapping, exactQuery, k)
          exact <- if (exactOpt.isDefined) ZIO.fromOption(exactOpt) else indexAndSearch(dataset, exactMapping, testQuery, k)
          _ <- rc.save(exact)
          testOpt <- rc.find(dataset, testMapping, testQuery, k)
          test <- if (testOpt.isDefined) ZIO.fromOption(testOpt) else indexAndSearch(dataset, testMapping, testQuery, k)
          _ <- rc.save(setRecalls(exact, test))
        } yield ()
      }
      _ <- ZIO.collectAll(testEffects)
    } yield ()
  }

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = parser.parse(args, Params()) match {
    case Some(params) =>
      val layer =
        (Blocking.live ++ ZLayer.succeed(AmazonS3ClientBuilder.defaultClient())) >>>
          Console.live ++
            Clock.live ++
            Slf4jLogger.make((_, s) => s, Some(getClass.getCanonicalName)) ++
            DatasetClient.s3(params.datasetsBucket, params.datasetsPrefix) ++
            ResultClient.s3(params.resultsBucket, params.resultsPrefix) ++
            ElastiknnZioClient.fromFutureClient("localhost", 9200, true)

      val logic = for {
        _ <- putStrLn(params.experimentJsonBase64)
        experiment <- decodeExperiment(params.experimentJsonBase64)
        _ <- run(experiment)
      } yield ()
      logic
        .provideLayer(layer)
        .mapError(System.err.println)
        .fold(_ => 1, _ => 0)
    case None => sys.exit(1)
  }

}
