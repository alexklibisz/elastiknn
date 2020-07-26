package com.klibisz.elastiknn.benchmarks

import java.util.concurrent.TimeUnit

import com.amazonaws.services.s3.AmazonS3
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.benchmarks.codecs._
import com.klibisz.elastiknn.client.ElastiknnClient
import com.sksamuel.elastic4s.ElasticDsl.{clusterHealth, _}
import com.sksamuel.elastic4s.requests.common.HealthStatus
import com.sksamuel.elastic4s.requests.searches.SearchIterator
import com.sksamuel.elastic4s.{ElasticDsl, Hit, HitReader}
import io.circe.parser._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console._
import zio.duration.Duration
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.stream._

import scala.util.Try
import scala.util.hashing.MurmurHash3

/**
  * Executes a single experiment containing one exact mapping, one test mapping, and many test queries.
  */
object Execute extends App {

  final case class Params(experimentHash: String = "",
                          experimentsBucket: String = "",
                          experimentsPrefix: String = "",
                          datasetsBucket: String = "",
                          datasetsPrefix: String = "",
                          resultsBucket: String = "",
                          resultsPrefix: String = "",
                          parallelism: Int = java.lang.Runtime.getRuntime.availableProcessors(),
                          s3Minio: Boolean = false,
                          recompute: Boolean = false)

  private val parser = new scopt.OptionParser[Params]("Execute benchmark jobs") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[String]("experimentHash")
      .text("Hash used to lookup experiment in S3")
      .action((s, c) => c.copy(experimentHash = s))
      .required()
    opt[String]("experimentsBucket").action((x, c) => c.copy(experimentsBucket = x)).required()
    opt[String]("experimentsPrefix").action((x, c) => c.copy(experimentsPrefix = x))
    opt[String]("datasetsBucket").action((s, c) => c.copy(datasetsBucket = s)).required()
    opt[String]("datasetsPrefix").action((s, c) => c.copy(datasetsPrefix = s))
    opt[String]("resultsBucket").action((s, c) => c.copy(resultsBucket = s)).required()
    opt[String]("resultsPrefix").action((s, c) => c.copy(resultsPrefix = s))
    opt[Int]("parallelism").action((i, c) => c.copy(parallelism = i))
    opt[Boolean]("s3Minio").action((b, c) => c.copy(s3Minio = b))
  }

  private def readExperiment(bucket: String, prefix: String, hash: String) =
    for {
      blocking <- ZIO.access[Blocking](_.get)
      s3Client <- ZIO.access[Has[AmazonS3]](_.get)
      body <- blocking.effectBlocking(s3Client.getObjectAsString(bucket, s"$prefix/$hash.json"))
      exp <- ZIO.fromEither(decode[Experiment](body))
    } yield exp

  private def indexAndSearch(
      dataset: Dataset,
      eknnMapping: Mapping,
      eknnQuery: NearestNeighborsQuery,
      k: Int,
      parallelism: Int): ZIO[Logging with Clock with DatasetClient with ElastiknnZioClient, Throwable, BenchmarkResult] = {

    // Index name is a function of dataset, mapping and holdout so we can check if it already exists and avoid re-indexing.
    val trainIndex = s"ix-${dataset.name}-${MurmurHash3.orderedHash(Seq(dataset, eknnMapping))}".toLowerCase
    val testIndex = s"$trainIndex-test"
    val storedIdField = "id"

    // Create a primary and holdout index with same mappings.
    // Split stream of vectors into primary and holdout vectors and index them separately.
    // Return the holdout ids so they can be consumed to run queries.
    def buildIndex(chunkSize: Int = 500) = {
      for {
        eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
        _ <- log.info(s"Creating index $trainIndex with mapping $eknnMapping and parallelism $parallelism")
        _ <- eknnClient.execute(createIndex(trainIndex).replicas(0).shards(parallelism).indexSetting("refresh_interval", "-1"))
        _ <- eknnClient.putMapping(trainIndex, eknnQuery.field, storedIdField, eknnMapping)
        _ <- eknnClient.execute(createIndex(testIndex).replicas(0).shards(parallelism).indexSetting("refresh_interval", "-1"))
        _ <- eknnClient.putMapping(testIndex, eknnQuery.field, storedIdField, eknnMapping)
        datasets <- ZIO.access[DatasetClient](_.get)
        _ <- log.info(s"Indexing vectors for dataset $dataset")
        _ <- datasets.streamTrain(dataset).take(200000).grouped(chunkSize).zipWithIndex.foreach {
          case (vecs, batchIndex) =>
            val ids = vecs.indices.map(i => s"$batchIndex-$i")
            for {
              (dur, _) <- eknnClient.index(trainIndex, eknnQuery.field, vecs, storedIdField, ids).timed
              _ <- log.debug(s"Indexed batch $batchIndex to $trainIndex in ${dur.toMillis} ms")
            } yield ()
        }
        _ <- datasets.streamTest(dataset).take(1000).grouped(chunkSize).zipWithIndex.foreach {
          case (vecs, batchIndex) =>
            val ids = vecs.indices.map(i => s"$batchIndex-$i")
            for {
              (dur, _) <- eknnClient.index(testIndex, eknnQuery.field, vecs, storedIdField, ids).timed
              _ <- log.debug(s"Indexed batch $batchIndex to $testIndex in ${dur.toMillis} ms")
            } yield ()
        }
        _ <- eknnClient.execute(refreshIndex(trainIndex, testIndex))
        _ <- eknnClient.execute(forceMerge(trainIndex, testIndex).maxSegments(1))
      } yield ()
    }

    def streamFromIndex(index: String, chunkSize: Int = 200) = {
      implicit val vecReader: HitReader[Vec] = (hit: Hit) =>
        for {
          json <- io.circe.parser.parse(hit.sourceAsString).toTry
          inner <- Try((json \\ eknnQuery.field).head)
          vec <- ElasticsearchCodec.decode[Vec](inner.hcursor).toTry
        } yield vec
      implicit val timeout: concurrent.duration.Duration = concurrent.duration.Duration("30 seconds")
      for {
        eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
        _ <- log.info(s"Streaming vectors from $index")
        query = ElasticDsl.search(index).scroll("5m").size(chunkSize).matchAllQuery()
        searchIter = SearchIterator.iterate[Vec](eknnClient.elasticClient, query)
      } yield Stream.fromIterator(searchIter).chunkN(chunkSize)
    }

    def search(testVecs: Stream[Throwable, Vec]) = {
      for {
        eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
        requests = testVecs.zipWithIndex.mapMPar(parallelism) {
          case (vec, i) =>
            for {
              (dur, res) <- eknnClient.nearestNeighbors(trainIndex, eknnQuery.withVec(vec), k, storedIdField).timed
              _ <- if (i % 10 == 0) log.debug(s"Completed query $i in ${dur.toMillis} ms") else ZIO.succeed(())
            } yield QueryResult(res.result.hits.hits.map(_.id), res.result.took)
        }
        (dur, responses) <- requests.run(ZSink.collectAll).timed
      } yield (responses, dur.toMillis)
    }

    for {
      eknnClient <- ZIO.access[ElastiknnZioClient](_.get)

      // Check if the index already exists.
      _ <- log.info(s"Checking for index $trainIndex with mapping $eknnMapping")
      trainExists <- eknnClient.execute(indexExists(trainIndex)).map(_.result.exists).catchSome {
        case _: ElastiknnClient.StrictFailureException => ZIO.succeed(false)
      }
      testExists <- eknnClient.execute(indexExists(testIndex)).map(_.result.exists).catchSome {
        case _: ElastiknnClient.StrictFailureException => ZIO.succeed(false)
      }

      // Create the index if primary and holdout don't exist.
      _ <- if (trainExists && testExists)
        log.info(s"Found indices $trainIndex and $testIndex")
      else buildIndex()

      // Load a stream of vectors from the holdout index.
      testVecs <- streamFromIndex(testIndex)

      // Run searches on the holdout vectors.
      (singleResults, totalDuration) <- search(testVecs)
      _ <- log.info(s"Completed ${singleResults.length} searches in ${totalDuration / 1000f} seconds")

    } yield BenchmarkResult(dataset, eknnMapping, eknnQuery, k, parallelism, totalDuration, singleResults)
  }

  private def setRecalls(exact: BenchmarkResult, test: BenchmarkResult): BenchmarkResult = {
    val withRecalls = exact.queryResults.zip(test.queryResults).map {
      case (ex, ts) => ts.copy(recall = ex.neighbors.intersect(ts.neighbors).length * 1d / ex.neighbors.length)
    }
    test.copy(queryResults = withRecalls)
  }

  private def run(experiment: Experiment, parallelism: Int, recompute: Boolean) = {
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
            case _ =>
              for {
                exact <- indexAndSearch(dataset, exactMapping, exactQuery, k, parallelism)
                _ <- log.info(s"Saving exact result: $exact")
                _ <- rc.save(setRecalls(exact, exact))
              } yield exact
          }

          testOpt <- rc.find(dataset, testMapping, testQuery, k)
          _ <- testOpt match {
            case Some(_) if !recompute => log.info(s"Found test result for mapping $testMapping, query $testQuery")
            case _ =>
              for {
                test <- indexAndSearch(dataset, testMapping, testQuery, k, parallelism).map(setRecalls(exact, _))
                aggregate = AggregateResult(test)
                _ <- log.info(s"Saving test result: $test")
                _ <- log.info(s"Aggregate: $aggregate")
                _ <- rc.save(test)
              } yield ()
          }
        } yield ()
      }
      _ <- ZIO.collectAll(testEffects)
    } yield ()
  }

  def apply(params: Params): ZIO[Any, Throwable, Unit] = {
    val s3Client = if (params.s3Minio) S3Utils.minioClient() else S3Utils.defaultClient()
    val blockingWithS3 = Blocking.live ++ ZLayer.succeed(s3Client)
    val layer =
      Console.live ++
        Clock.live ++
        blockingWithS3 ++
        (blockingWithS3 >>> ResultClient.s3(params.resultsBucket, params.resultsPrefix)) ++
        (blockingWithS3 >>> DatasetClient.s3(params.datasetsBucket, params.datasetsPrefix)) ++
        ElastiknnZioClient.fromFutureClient("localhost", 9200, true) ++
        Slf4jLogger.make((_, s) => s, Some(getClass.getSimpleName))

    val logic = for {

      // Load the experiment.
      _ <- log.info(params.toString)
      experiment <- readExperiment(params.experimentsBucket, params.experimentsPrefix, params.experimentHash)
      _ <- log.info(s"Running experiment: $experiment")

      // Wait for cluster ready.
      _ <- log.info("Waiting for cluster")
      eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
      check = clusterHealth.waitForStatus(HealthStatus.Yellow).timeout("60s")
      _ <- eknnClient.execute(check).retry(Schedule.recurs(10) && Schedule.spaced(Duration(10, TimeUnit.SECONDS)))
      _ <- log.info("Cluster ready")

      // Run the experiment.
      _ <- run(experiment, params.parallelism, params.recompute)

    } yield ()

    logic.provideLayer(layer)
  }

  override def run(args: List[String]): URIO[Console, ExitCode] = parser.parse(args, Params()) match {
    case Some(params) => apply(params).exitCode
    case None         => sys.exit(1)
  }

}
