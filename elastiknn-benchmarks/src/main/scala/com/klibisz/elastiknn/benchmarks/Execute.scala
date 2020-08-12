package com.klibisz.elastiknn.benchmarks

import java.net.URI
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
import scala.concurrent.duration._
import scala.util.hashing.MurmurHash3

/**
  * Executes a single experiment containing one exact mapping, one test mapping, and many test queries.
  */
object Execute extends App {

  final case class Params(experimentKey: String = "",
                          experimentsPrefix: String = "",
                          datasetsPrefix: String = "",
                          resultsPrefix: String = "",
                          recompute: Boolean = false,
                          bucket: String = "",
                          s3Url: Option[String] = None,
                          esUrl: Option[String] = None,
                          shards: Int = 1,
                          parallelQueries: Int = 1)

  private val parser = new scopt.OptionParser[Params]("Execute benchmark jobs") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[String]("experimentKey")
      .text("s3 key where the experiment definition is stored")
      .action((s, c) => c.copy(experimentKey = s))
      .required()
    opt[String]("datasetsPrefix")
      .text("s3 key where default datasets are stored")
      .action((s, c) => c.copy(datasetsPrefix = s))
      .optional()
    opt[String]("resultsPrefix")
      .text("s3 prefix where results should be stored")
      .action((s, c) => c.copy(resultsPrefix = s))
      .required()
    opt[String]("bucket")
      .action((s, c) => c.copy(bucket = s))
      .text("bucket for all s3 data")
      .required()
    opt[String]("s3Url")
      .text("URL accessed by the s3 client")
      .action((s, c) => c.copy(s3Url = Some(s)))
      .optional()
    opt[String]("esUrl")
      .text("elasticsearch URL, e.g. http://localhost:9200")
      .action((s, c) => c.copy(s3Url = Some(s)))
      .optional()
    opt[Int]("shards")
      .text("number of shards in the elasticsearch index")
      .action((i, c) => c.copy(shards = i))
      .optional()
    opt[Int]("parallelQueries")
      .text("number of queries to execute in parallel")
      .action((i, c) => c.copy(shards = i))
      .optional()
  }

  private def readExperiment(bucket: String, prefix: String, key: String) =
    for {
      blocking <- ZIO.access[Blocking](_.get)
      s3Client <- ZIO.access[Has[AmazonS3]](_.get)
      body <- blocking.effectBlocking(s3Client.getObjectAsString(bucket, s"$prefix/$key.json"))
      exp <- ZIO.fromEither(decode[Experiment](body))
    } yield exp

  private def indexAndSearch(
      dataset: Dataset,
      eknnMapping: Mapping,
      eknnQuery: NearestNeighborsQuery,
      k: Int,
      shards: Int,
      parallelQueries: Int
  ) = {

    // Index name is a function of dataset, mapping and holdout so we can check if it already exists and avoid re-indexing.
    val trainIndex = s"ix-${dataset.name}-${MurmurHash3.orderedHash(Seq(dataset, eknnMapping))}".toLowerCase
    val storedIdField = "id"
    val vecField = "vec"

    def buildIndex(chunkSize: Int = 500) = {
      for {
        searchBackend <- ZIO.access[Has[SearchClient]](_.get)
        datasetClient <- ZIO.access[Has[DatasetClient]](_.get)
        _ <- log.info(s"Creating index [$trainIndex] with mapping [$eknnMapping] and [$shards] shards")
        _ <- searchBackend.buildIndex(trainIndex, vecField, eknnMapping, shards, datasetClient.streamTrain(dataset))

//        _ <- eknnClient.execute(createIndex(trainIndex).replicas(0).shards(parallelism).indexSetting("refresh_interval", "-1"))
//        _ <- eknnClient.putMapping(trainIndex, eknnQuery.field, storedIdField, eknnMapping)
//        _ <- eknnClient.execute(createIndex(testIndex).replicas(0).shards(parallelism).indexSetting("refresh_interval", "-1"))
//        _ <- eknnClient.putMapping(testIndex, eknnQuery.field, storedIdField, eknnMapping)
//        datasets <- ZIO.access[DatasetClient](_.get)
//        _ <- log.info(s"Indexing vectors for dataset $dataset")
//        _ <- datasets.streamTrain(dataset).grouped(chunkSize).zipWithIndex.foreach {
//          case (vecs, batchIndex) =>
//            val ids = vecs.indices.map(i => s"$batchIndex-$i")
//            for {
//              (dur, _) <- eknnClient.index(trainIndex, eknnQuery.field, vecs, storedIdField, ids).timed
//              _ <- log.debug(s"Indexed batch $batchIndex to $trainIndex in ${dur.toMillis} ms")
//            } yield ()
//        }
//        _ <- datasets.streamTest(dataset).grouped(chunkSize).zipWithIndex.foreach {
//          case (vecs, batchIndex) =>
//            val ids = vecs.indices.map(i => s"$batchIndex-$i")
//            for {
//              (dur, _) <- eknnClient.index(testIndex, eknnQuery.field, vecs, storedIdField, ids).timed
//              _ <- log.debug(s"Indexed batch $batchIndex to $testIndex in ${dur.toMillis} ms")
//            } yield ()
//        }
//        _ <- eknnClient.execute(refreshIndex(trainIndex, testIndex))
//        _ <- eknnClient.execute(forceMerge(trainIndex).maxSegments(1))
//        _ <- eknnClient.execute(forceMerge(testIndex).maxSegments(1))
//        _ <- eknnClient.execute(refreshIndex(trainIndex, testIndex))
//        _ <- ZIO.sleep(Duration(10, TimeUnit.SECONDS))
      } yield ()
    }

//    def streamFromIndex(index: String, chunkSize: Int = 200) = {
//      implicit val vecReader: HitReader[Vec] = (hit: Hit) =>
//        for {
//          json <- io.circe.parser.parse(hit.sourceAsString).toTry
//          inner <- Try((json \\ eknnQuery.field).head)
//          vec <- ElasticsearchCodec.decode[Vec](inner.hcursor).toTry
//        } yield vec
//      implicit val timeout: concurrent.duration.Duration = concurrent.duration.Duration("30 seconds")
//      for {
//        eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
//        _ <- log.info(s"Streaming vectors from $index")
//        query = ElasticDsl.search(index).scroll("5m").size(chunkSize).matchAllQuery()
//        searchIter = SearchIterator.iterate[Vec](eknnClient.elasticClient, query)
//      } yield Stream.fromIterator(searchIter).chunkN(chunkSize)
//    }
//
//    def search(testVecs: Stream[Throwable, Vec]) = {
//      for {
//        eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
//        requests = testVecs.zipWithIndex.mapMPar(parallelism) {
//          case (vec, i) =>
//            for {
//              (dur, res) <- eknnClient.nearestNeighbors(trainIndex, eknnQuery.withVec(vec), k, storedIdField).timed
//              _ <- if (i % 100 == 0) log.debug(s"Completed query $i in $trainIndex in ${dur.toMillis} ms") else ZIO.succeed(())
//            } yield QueryResult(res.result.hits.hits.map(_.id), res.result.took)
//        }
//        (dur, responses) <- requests.run(ZSink.collectAll).timed
//      } yield (responses, dur.toMillis)
//    }
//
    for {

      searchClient <- ZIO.access[Has[SearchClient]](_.get)
      datasetClient <- ZIO.access[Has[DatasetClient]](_.get)

      // Check if the index already exists.
      _ <- log.info(s"Checking for index $trainIndex with mapping $eknnMapping")
      trainExists <- searchClient.indexExists(trainIndex)

      // Create the index if doesn't exist.
      _ <- if (trainExists) log.info(s"Found index [$trainIndex]") else buildIndex()

      // Run searches on the test vectors.
      queryStream = datasetClient.streamTest(dataset).map(eknnQuery.withVec)
      resultsStream = searchClient.search(queryStream, parallelQueries)
      (dur, results) <- resultsStream.run(ZSink.collectAll).timed
      _ <- log.info(s"Completed [${results.length}] searches in [${dur.toMillis / 1000f}] seconds")

    } yield BenchmarkResult(dataset, eknnMapping, eknnQuery, k, ???, dur.toMillis, results)
  }

  private def setRecalls(exact: BenchmarkResult, test: BenchmarkResult): BenchmarkResult = {
    val withRecalls = exact.queryResults.zip(test.queryResults).map {
      case (ex, ts) => ts.copy(recall = ex.neighbors.intersect(ts.neighbors).length * 1d / ex.neighbors.length)
    }
    test.copy(queryResults = withRecalls)
  }

  private def run(experiment: Experiment, shards: Int, parallelQueries: Int, recompute: Boolean) = {
    import experiment._
    for {
      resultsClient <- ZIO.access[Has[ResultClient]](_.get)
      searchBackend <- ZIO.access[Has[SearchClient]](_.get)
      testEffects = experiment.testQueries.map {
        case Query(testQuery, k) =>
          for {
            exactOpt <- resultsClient.find(dataset, exactMapping, exactQuery, k)
            exactRes <- exactOpt match {
              case Some(res) =>
                log
                  .info(s"Found exact result for mapping [$exactMapping] query [$exactQuery]")
                  .map(_ => res)
              case None =>
                for {
                  _ <- log.info(s"Found no result for mapping [$exactMapping], query [$exactQuery]")
                  exact <- indexAndSearch(dataset, exactMapping, exactQuery, k, shards, parallelQueries)
                  _ <- log.info(s"Saving exact result for mapping [$exactMapping], query [$exactQuery]: $exact")
                  _ <- resultsClient.save(setRecalls(exact, exact))
                } yield exact
            }

            testOpt <- resultsClient.find(dataset, testMapping, testQuery, k)
            _ <- testOpt match {
              case Some(_) if !recompute =>
                log.info(s"Found existing test result for mapping [$testMapping], query [$testQuery]")
              case _ =>
                for {
                  test <- indexAndSearch(dataset, testMapping, testQuery, k, shards, parallelQueries).map(setRecalls(exactRes, _))
                  _ <- log.info(s"Saving test result for mapping [$testMapping], query [$testQuery]: $test")
                  _ <- resultsClient.save(test)
                  aggregate = AggregateResult(test)
                  _ <- log.info(s"Aggregate test result for mapping [$testMapping], query [$testQuery]: $aggregate")
                } yield ()
            }
          } yield ()
      }
      _ <- ZIO.collectAll(testEffects)
    } yield ()
  }

  def apply(params: Params): ZIO[Any, Throwable, Unit] = {
    import params._
    val s3Client = S3Utils.client(s3Url)
    val blockingWithS3 = Blocking.live ++ ZLayer.succeed(s3Client)

    val loggingLayer = Slf4jLogger.make((_, s) => s, Some(this.getClass.getSimpleName))

    val searchClientLayer: Layer[Throwable, Has[SearchClient]] = esUrl match {
      case Some(url) =>
        (loggingLayer ++ Clock.live) >>> SearchClient.elasticsearch(URI.create(url), true, 99999)
      case None =>
        SearchClient.luceneInMemory()
    }

    val layer =
      Console.live ++
        Clock.live ++
        blockingWithS3 ++
        (blockingWithS3 >>> ResultClient.s3(bucket, resultsPrefix)) ++
        (blockingWithS3 >>> DatasetClient.s3(bucket, datasetsPrefix)) ++
        loggingLayer ++
        searchClientLayer

    val steps = for {

      // Load the experiment.
      _ <- log.info(params.toString)
      experiment <- readExperiment(bucket, experimentsPrefix, experimentKey)
      _ <- log.info(s"Running experiment: $experiment")

      // Wait for cluster ready.
      _ <- log.info("Waiting for cluster")
      searchBackend <- ZIO.access[Has[SearchClient]](_.get)
      _ <- searchBackend.blockUntilReady()

      // Run the experiment.
      _ <- run(experiment, shards, parallelQueries, recompute)

    } yield ()

    steps.provideLayer(layer)
  }

  override def run(args: List[String]): URIO[Console, ExitCode] = parser.parse(args, Params()) match {
    case Some(params) => apply(params).exitCode
    case None         => sys.exit(1)
  }

}
