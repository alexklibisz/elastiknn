package com.klibisz.elastiknn.benchmarks

import java.util.Base64
import java.util.concurrent.TimeUnit

import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.benchmarks.codecs._
import com.klibisz.elastiknn.client.ElastiknnClient
import com.sksamuel.elastic4s.ElasticDsl.clusterHealth
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

import com.sksamuel.elastic4s.ElasticDsl._

import scala.util.Try
import scala.util.hashing.MurmurHash3

/**
  * Executes a single experiment containing one exact mapping, one test mapping, and many test queries.
  */
object Execute extends App {

  private case class Params(experimentJsonBase64: String = "",
                            datasetsBucket: String = "",
                            datasetsPrefix: String = "",
                            resultsBucket: String = "",
                            resultsPrefix: String = "",
                            holdoutProportion: Double = 0.1,
                            parallelism: Int = 8)

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
    opt[Int]("parallelism").action((i, c) => c.copy(parallelism = i))
  }

  private val decoder = Base64.getDecoder

  private def decodeExperiment(jsonBase64: String): IO[Throwable, Experiment] =
    for {
      jsonString <- ZIO.fromTry(Try(new String(decoder.decode(jsonBase64))))
      experiment <- ZIO.fromEither(decode[Experiment](jsonString))
    } yield experiment

  // Map from dataset and holdout proportion to the holdout vectors for that dataset.
  // This lets us avoid having to re-read the dataset to get holdout vectors.
  // Mutable maps arent' the best approach, but in practice shouldn't be a problem since each experiment
  // will have just one set of holdout vectors.
  private val holdoutCache = scala.collection.mutable.HashMap.empty[(Dataset, Double), Vector[Vec]]

  private def indexAndSearch(
      dataset: Dataset,
      eknnMapping: Mapping,
      eknnQuery: NearestNeighborsQuery,
      k: Int,
      holdoutProportion: Double,
      parallelism: Int): ZIO[Console with Clock with Logging with DatasetClient with ElastiknnZioClient, Throwable, BenchmarkResult] = {

    // Index name is a function of dataset, mapping and holdout so we can check if it already exists and avoid re-indexing.
    val indexName = s"ix-${dataset.name}-${MurmurHash3.orderedHash(Seq(eknnMapping, holdoutProportion))}"

    def buildIndex() = {
      for {
        eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
        _ <- log.info(s"Creating index $indexName with mapping $eknnMapping and $parallelism shards")
        _ <- eknnClient.execute(createIndex(indexName).replicas(0).shards(parallelism))
        _ <- eknnClient.putMapping(indexName, eknnQuery.field, eknnMapping)
        datasets <- ZIO.access[DatasetClient](_.get)
        _ <- log.info(s"Streaming vectors for dataset $dataset")
        stream = datasets.stream[Vec](dataset)
        holdoutVecs <- stream.grouped(500).zipWithIndex.foldM(Vector.empty[Vec]) {
          case (acc, (vecs, batchIndex)) =>
            val (holdoutVecs, indexVecs) = vecs.splitAt((vecs.length * holdoutProportion).toInt)
            val ids = indexVecs.indices.map(i => s"b$batchIndex-$i")
            for {
              (dur, _) <- eknnClient.index(indexName, eknnQuery.field, indexVecs, Some(ids)).timed
              _ <- log.debug(s"Indexed batch $batchIndex to $indexName w/ ${indexVecs.length} vecs in ${dur.toMillis} ms")
            } yield acc ++ holdoutVecs
        }
      } yield holdoutVecs
    }

    def search(holdout: Vector[Vec]) = {
      for {
        eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
        requests = holdout.zipWithIndex.map {
          case (vec, i) =>
            for {
              (dur, res) <- eknnClient.nearestNeighbors(indexName, eknnQuery.withVec(vec), k).timed
              _ <- log.debug(s"Completed query ${i + 1} of ${holdout.length} in ${dur.toMillis} ms")
            } yield res
        }
        (dur, responses) <- ZIO.collectAllParN(parallelism)(requests).timed
      } yield (responses.map(r => QueryResult(r.result.hits.hits.map(_.id), r.result.took)), dur.toMillis)
    }

    for {
      eknnClient <- ZIO.access[ElastiknnZioClient](_.get)

      // Check if the index already exists.
      _ <- log.info(s"Checking for index $indexName with mapping $eknnMapping")
      exists <- eknnClient.execute(indexExists(indexName)).map(_.result.exists).catchSome {
        case _: ElastiknnClient.StrictFailureException => ZIO.succeed(false)
      }

      // If it doesn't, index the dataset.
      holdouts <- if (exists && holdoutCache.contains((dataset, holdoutProportion))) {
        for {
          _ <- log.info(s"Found the index $indexName")
        } yield holdoutCache((dataset, holdoutProportion))
      } else {
        for {
          holdouts <- buildIndex()
          _ = holdoutCache.put((dataset, holdoutProportion), holdouts)
        } yield holdouts
      }

      // Run searches on the holdout vectors.
      _ <- log.info(s"Searching ${holdouts.length} holdout vectors with query $eknnQuery")
      (singleResults, totalDuration) <- search(holdouts)

    } yield BenchmarkResult(dataset, eknnMapping, eknnQuery, k, totalDuration, parallelism, singleResults)
  }

  private def setRecalls(exact: BenchmarkResult, test: BenchmarkResult): BenchmarkResult = {
    val withRecalls = exact.queryResults.zip(test.queryResults).map {
      case (ex, ts) => ts.copy(recall = ex.neighbors.intersect(ts.neighbors).length * 1d / ex.neighbors.length)
    }
    test.copy(queryResults = withRecalls)
  }

  private def run(experiment: Experiment, holdoutProportion: Double, parallelism: Int) = {
    import experiment._
    for {
      rc <- ZIO.access[ResultClient](_.get)
      testEffects = for {
        Query(testQuery, k) <- experiment.testQueries
      } yield {
        for {
          exactOpt <- rc.find(dataset, exactMapping, exactQuery, k)
          exact <- exactOpt match {
            case Some(res) =>
              for {
                _ <- log.info(s"Found exact result for mapping $exactMapping, query $exactQuery")
              } yield res
            case None =>
              for {
                exact <- indexAndSearch(dataset, exactMapping, exactQuery, k, holdoutProportion, parallelism)
                _ <- log.info(s"Saving exact result for mapping $exactMapping, query $exactQuery")
                _ <- rc.save(setRecalls(exact, exact))
              } yield exact
          }

          testOpt <- rc.find(dataset, testMapping, testQuery, k)
          _ <- testOpt match {
            case Some(_) => log.info(s"Found test result for mapping $testMapping, query $testQuery")
            case None =>
              for {
                test <- indexAndSearch(dataset, testMapping, testQuery, k, holdoutProportion, parallelism)
                _ <- log.info(s"Saving test result for mapping $testMapping, query $testQuery")
                _ <- rc.save(setRecalls(exact, test))
              } yield ()
          }
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
            Slf4jLogger.make((_, s) => s, Some(getClass.getSimpleName)) ++
            DatasetClient.s3(params.datasetsBucket, params.datasetsPrefix) ++
            ResultClient.s3(params.resultsBucket, params.resultsPrefix) ++
            ElastiknnZioClient.fromFutureClient("localhost", 9200, true)

      val logic = for {

        // Parse the experiment.
        _ <- log.info(params.toString)
        experiment <- decodeExperiment(params.experimentJsonBase64)
        _ <- log.info(s"Running experiment: $experiment")

        // Wait for cluster ready.
        _ <- log.info(s"Waiting for cluster")
        eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
        check = clusterHealth.waitForStatus(HealthStatus.Yellow).timeout("60s")
        _ <- eknnClient.execute(check).retry(Schedule.recurs(10) && Schedule.spaced(Duration(10, TimeUnit.SECONDS)))

        // Run the experiment.
        _ <- run(experiment, params.holdoutProportion, params.parallelism)
        _ <- log.info("Done - exiting successfully")

      } yield ()
      logic
        .provideLayer(layer)
        .mapError(System.err.println)
        .fold(_ => 1, _ => 0)
    case None => sys.exit(1)
  }

}
