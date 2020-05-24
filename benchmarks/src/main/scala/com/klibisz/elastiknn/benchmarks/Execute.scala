package com.klibisz.elastiknn.benchmarks

import java.util.Base64
import java.util.concurrent.TimeUnit

import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.benchmarks.codecs._
import com.klibisz.elastiknn.client.ElastiknnClient
import com.sksamuel.elastic4s.requests.common.HealthStatus
import io.circe.parser._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.logging._
import zio.console._
import zio.duration.Duration
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger

import scala.util.Try
import scala.util.hashing.MurmurHash3

object Execute extends App {

  private case class Params(experimentJsonBase64: String = "",
                            datasetsBucket: String = "",
                            datasetsPrefix: String = "",
                            resultsBucket: String = "",
                            resultsPrefix: String = "",
                            holdoutProportion: Double = 0f)
  private case class QueryResult(neighborIds: Seq[String], duration: Duration)

  private val parser = new scopt.OptionParser[Params]("Execute benchmark jobs") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[String]("experimentJsonBase64")
      .text("Experiment case class serialized as a Json string")
      .action((s, c) => c.copy(experimentJsonBase64 = s))
      .required()
    opt[String]("datasetsBucket").action((s, c) => c.copy(datasetsBucket = s)).required()
    opt[String]("datasetsPrefix").action((s, c) => c.copy(datasetsPrefix = s))
    opt[String]("resultsBucket").action((s, c) => c.copy(resultsBucket = s)).required()
    opt[String]("resultsPrefix").action((s, c) => c.copy(resultsPrefix = s))
    opt[Double]("holdoutProportion").action((d, c) => c.copy(holdoutProportion = d))
  }

  private val decoder = Base64.getDecoder

  private def decodeExperiment(jsonBase64: String): IO[Throwable, Experiment] =
    for {
      jsonString <- ZIO.fromTry(Try(new String(decoder.decode(jsonBase64))))
      experiment <- ZIO.fromEither(decode[Experiment](jsonString))
    } yield experiment

  private def indexAndSearch(dataset: Dataset,
                             eknnMapping: Mapping,
                             eknnQuery: NearestNeighborsQuery,
                             k: Int,
                             holdoutProportion: Double): ZIO[Console with Clock with Logging with DatasetClient with ElastiknnZioClient, Throwable, Result] = {

    import com.sksamuel.elastic4s.ElasticDsl._

    def buildIndex(indexName: String) = {
      for {
        elastiknn <- ZIO.access[ElastiknnZioClient](_.get)
        _ <- elastiknn.execute(createIndex(indexName).replicas(0).shards(java.lang.Runtime.getRuntime.availableProcessors()))
        _ <- elastiknn.putMapping(indexName, eknnQuery.field, eknnMapping)
        datasets <- ZIO.access[DatasetClient](_.get)
        _ <- log.info(s"Streaming vectors for dataset $dataset")
        stream = datasets.stream[Vec](dataset)
        holdoutVecs <- stream.grouped(500).zipWithIndex.foldM(Vector.empty[Vec]) {
          case (acc, (vecs, batchIndex)) =>
            val (holdoutVecs, indexVecs) = vecs.splitAt((vecs.length * holdoutProportion).toInt)
            val ids = indexVecs.map(i => s"v${batchIndex}-$i")
            for {
              (dur, _) <- elastiknn.index(indexName, eknnQuery.field, indexVecs, Some(ids)).timed
              _ <- log.debug(s"Indexed batch $batchIndex to ${indexName} containing ${indexVecs.length} vectors in ${dur.toMillis} ms")
            } yield acc ++ holdoutVecs
        }
      } yield indexName -> holdoutVecs
    }

    // Index name is a function of dataset, mapping and holdout so we can check if it already exists and avoid re-indexing.
    val indexName = s"ix-${MurmurHash3.orderedHash(Seq(dataset, eknnMapping, holdoutProportion))}"

    for {
      elastiknn <- ZIO.access[ElastiknnZioClient](_.get)

      // Wait for cluster ready.
      _ <- log.info(s"Waiting for cluster")
      _ <- ZIO.sleep(Duration(30, TimeUnit.SECONDS))
      _ <- elastiknn.execute(clusterHealth().waitForStatus(HealthStatus.Yellow).timeout("60s"))

      // Check if the index already exists.
      _ <- log.info(s"Checking for index $indexName")
      exists <- elastiknn.execute(indexExists(indexName)).map(_.result.exists).catchSome {
        case _: ElastiknnClient.StrictFailureException => ZIO.succeed(false)
      }

      // If it doesn't, index the dataset.
      _ <- if (exists) ZIO.succeed(()) else buildIndex(indexName)

    } yield Result(dataset, eknnMapping, eknnQuery, k, Seq.empty)
  }

  private def setRecalls(exact: Result, test: Result): Result = {
    val withRecalls = exact.singleResults.zip(test.singleResults).map {
      case (ex, ts) => ts.copy(recall = ex.neighbors.intersect(ts.neighbors).length * 1d / ex.neighbors.length)
    }
    test.copy(singleResults = withRecalls)
  }

  private def run(experiment: Experiment,
                  holdoutProportion: Double): ZIO[Console with Clock with Logging with DatasetClient with ElastiknnZioClient with ResultClient, Any, Unit] = {
    import experiment._
    for {
      rc <- ZIO.access[ResultClient](_.get)
      testEffects = for {
        Query(testQuery, k) <- experiment.testQueries
      } yield {
        for {
          exactOpt <- rc.find(dataset, exactMapping, exactQuery, k)
          exact <- if (exactOpt.isDefined) ZIO.fromOption(exactOpt) else indexAndSearch(dataset, exactMapping, testQuery, k, holdoutProportion)
          _ <- rc.save(exact)
          testOpt <- rc.find(dataset, testMapping, testQuery, k)
          test <- if (testOpt.isDefined) ZIO.fromOption(testOpt) else indexAndSearch(dataset, testMapping, testQuery, k, holdoutProportion)
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
        _ <- log.info(params.toString)
        experiment <- decodeExperiment(params.experimentJsonBase64)
        _ <- run(experiment, params.holdoutProportion)
      } yield ()
      logic
        .provideLayer(layer)
        .mapError(System.err.println)
        .fold(_ => 1, _ => 0)
    case None => sys.exit(1)
  }

}
