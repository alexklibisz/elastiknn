package com.klibisz.elastiknn.benchmarks

import java.util.Base64
import java.util.concurrent.TimeUnit

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{AWSCredentials, AWSStaticCredentialsProvider}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
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

  final case class Params(experimentJsonBase64: String = "",
                          datasetsBucket: String = "",
                          datasetsPrefix: String = "",
                          resultsBucket: String = "",
                          resultsPrefix: String = "",
                          holdoutProportion: Double = 0.1,
                          shards: Int = java.lang.Runtime.getRuntime.availableProcessors(),
                          s3Minio: Boolean = false,
                          skipExisting: Boolean = true)

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
    opt[Int]("shards").action((i, c) => c.copy(shards = i))
    opt[Boolean]("s3Minio").action((b, c) => c.copy(s3Minio = b))
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
                             holdoutProportion: Double,
                             shards: Int) = {

    // Index name is a function of dataset, mapping and holdout so we can check if it already exists and avoid re-indexing.
    val primaryIndexName = s"ix-${dataset.name}-${MurmurHash3.orderedHash(Seq(eknnMapping, holdoutProportion))}"
    val holdoutIndexName = s"${primaryIndexName}-holdouts"

    // Create a primary and holdout index with same mappings.
    // Split stream of vectors into primary and holdout vectors and index them separately.
    // Return the holdout ids so they can be consumed to run queries.
    def buildIndex() = {
      for {
        eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
        _ <- log.info(s"Creating index $primaryIndexName with mapping $eknnMapping and $shards shards")
        _ <- eknnClient.execute(createIndex(primaryIndexName).replicas(0).shards(shards))
        _ <- eknnClient.putMapping(primaryIndexName, eknnQuery.field, eknnMapping)
        _ <- eknnClient.execute(createIndex(holdoutIndexName).replicas(0).shards(shards))
        _ <- eknnClient.putMapping(holdoutIndexName, eknnQuery.field, eknnMapping)
        datasets <- ZIO.access[DatasetClient](_.get)
        _ <- log.info(s"Streaming vectors for dataset $dataset")
        _ <- datasets.stream[Vec](dataset).grouped(500).zipWithIndex.foreach {
          case (vecs, batchIndex) =>
            val (holdoutVecs, primaryVecs) = vecs.splitAt((vecs.length * holdoutProportion).toInt)
            val primaryIds = primaryVecs.indices.map(i => s"b$batchIndex-$i")
            val holdoutIds = holdoutVecs.indices.map(i => s"b$batchIndex-$i")
            for {
              (dur, _) <- eknnClient.index(primaryIndexName, eknnQuery.field, primaryVecs, Some(primaryIds)).timed
              _ <- log.debug(s"Indexed batch $batchIndex to $primaryIndexName w/ ${primaryVecs.length} vecs in ${dur.toMillis} ms")
              _ <- eknnClient.index(holdoutIndexName, eknnQuery.field, holdoutVecs, Some(holdoutIds))
            } yield ()
        }
      } yield ()
    }

    def search(holdoutVecs: Stream[Throwable, Vec]) = {
      for {
        eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
        requests = holdoutVecs.zipWithIndex.mapM {
          case (vec, i) =>
            for {
              (dur, res) <- eknnClient.nearestNeighbors(primaryIndexName, eknnQuery.withVec(vec), k).timed
              _ <- log.debug(s"Completed query ${i + 1} in ${dur.toMillis} ms")
            } yield res
        }

        // Execute queries serially so that the effect of parallel shards is not affected by parallel requests.
        (dur, responses) <- requests.run(Sink.collectAll).timed
      } yield (responses.map(r => QueryResult(r.result.hits.hits.map(_.id), r.result.took)), dur.toMillis)
    }

    def streamHoldouts() = {
      implicit val vecReader: HitReader[Vec] = (hit: Hit) =>
        for {
          json <- io.circe.parser.parse(hit.sourceAsString).toTry
          inner <- Try((json \\ eknnQuery.field).head)
          vec <- ElasticsearchCodec.decode[Vec](inner.hcursor).toTry
        } yield vec
      implicit val timeout: concurrent.duration.Duration = concurrent.duration.Duration.fromNanos(3e10)

      for {
        eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
      } yield {
        val query = ElasticDsl.search(holdoutIndexName).scroll("10m").matchAllQuery().size(500)
        val searchIter = SearchIterator.iterate[Vec](eknnClient.elasticClient, query)
        Stream.fromIterator(searchIter)
      }
    }

    for {
      eknnClient <- ZIO.access[ElastiknnZioClient](_.get)

      // Check if the index already exists.
      _ <- log.info(s"Checking for index $primaryIndexName with mapping $eknnMapping")
      primaryExists <- eknnClient.execute(indexExists(primaryIndexName)).map(_.result.exists).catchSome {
        case _: ElastiknnClient.StrictFailureException => ZIO.succeed(false)
      }
      holdoutExists <- eknnClient.execute(indexExists(holdoutIndexName)).map(_.result.exists).catchSome {
        case _: ElastiknnClient.StrictFailureException => ZIO.succeed(false)
      }

      // Create the index if primary and holdout don't exist.
      _ <- if (primaryExists && holdoutExists)
        log.info(s"Found indices $primaryIndexName and $holdoutIndexName")
      else buildIndex()

      // Load a stream of vectors from the holdout index.
      holdouts <- streamHoldouts()

      // Run searches on the holdout vectors.
      // _ <- log.info(s"Searching ${holdoutIds.length} holdout vectors with query $eknnQuery")
      (singleResults, totalDuration) <- search(holdouts)

    } yield BenchmarkResult(dataset, eknnMapping, eknnQuery, k, shards, totalDuration, singleResults)
  }

  private def setRecalls(exact: BenchmarkResult, test: BenchmarkResult): BenchmarkResult = {
    val withRecalls = exact.queryResults.zip(test.queryResults).map {
      case (ex, ts) => ts.copy(recall = ex.neighbors.intersect(ts.neighbors).length * 1d / ex.neighbors.length)
    }
    test.copy(queryResults = withRecalls)
  }

  private def run(experiment: Experiment, holdoutProportion: Double, parallelism: Int, skipExisting: Boolean) = {
    import experiment._
    for {
      rc <- ZIO.access[ResultClient](_.get)
      testEffects = for {
        Query(testQuery, k) <- experiment.testQueries
      } yield {
        for {
          exactOpt <- rc.find(dataset, exactMapping, exactQuery, k)
          exact <- exactOpt match {
            case Some(res) if skipExisting =>
              for {
                _ <- log.info(s"Found exact result for mapping $exactMapping, query $exactQuery")
              } yield res
            case _ =>
              for {
                exact <- indexAndSearch(dataset, exactMapping, exactQuery, k, holdoutProportion, parallelism)
                _ <- log.info(s"Saving exact result: $exact: ${exact.queriesPerSecondPerShard} queries/sec")
                _ <- rc.save(setRecalls(exact, exact))
              } yield exact
          }

          testOpt <- rc.find(dataset, testMapping, testQuery, k)
          _ <- testOpt match {
            case Some(_) if skipExisting => log.info(s"Found test result for mapping $testMapping, query $testQuery")
            case _ =>
              for {
                test: BenchmarkResult <- indexAndSearch(dataset, testMapping, testQuery, k, holdoutProportion, parallelism)
                _ <- log.info(s"Saving test result: $test: ${test.queriesPerSecondPerShard} queries/sec")
                _ <- rc.save(setRecalls(exact, test))
              } yield ()
          }
        } yield ()
      }
      _ <- ZIO.collectAll(testEffects)
    } yield ()
  }

  private def s3Client(s3Minio: Boolean): AmazonS3 =
    if (s3Minio) {
      // Setup for Minio: https://docs.min.io/docs/how-to-use-aws-sdk-for-java-with-minio-server.html
      val endpointConfig = new EndpointConfiguration("http://localhost:9000", "us-east-1")
      val clientConfig = new ClientConfiguration()
      clientConfig.setSignerOverride("AWSS3V4SignerType")
      AmazonS3ClientBuilder.standard
        .withPathStyleAccessEnabled(true)
        .withEndpointConfiguration(endpointConfig)
        .withClientConfiguration(clientConfig)
        .withCredentials(new AWSStaticCredentialsProvider(new AWSCredentials {
          override def getAWSAccessKeyId: String = "minioadmin"
          override def getAWSSecretKey: String = "minioadmin"
        }))
        .build()
    } else AmazonS3ClientBuilder.defaultClient()

  def apply(params: Params): ZIO[Any, Throwable, Unit] = {
    val layer =
      (Blocking.live ++ ZLayer.succeed(s3Client(params.s3Minio))) >>>
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
      _ <- log.info("Waiting for cluster")
      eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
      check = clusterHealth.waitForStatus(HealthStatus.Yellow).timeout("60s")
      _ <- eknnClient.execute(check).retry(Schedule.recurs(10) && Schedule.spaced(Duration(10, TimeUnit.SECONDS)))
      _ <- log.info("Cluster ready")

      // Run the experiment.
      _ <- run(experiment, params.holdoutProportion, params.shards, params.skipExisting)
      _ <- log.info("Done - exiting successfully")

    } yield ()
    logic.provideLayer(layer)
  }

  override def run(args: List[String]): URIO[Console, ExitCode] = parser.parse(args, Params()) match {
    case Some(params) => apply(params).exitCode
    case None         => sys.exit(1)
  }

}

object ExecuteLocalSparseBool extends App {

  override def run(args: List[String]): URIO[Console, ExitCode] =
    Execute(
      Execute.Params(
        experimentJsonBase64 = Experiment(
          Dataset.RandomSparseBool(4096, 10000),
          exactMapping = Mapping.SparseBool(4096),
          exactQuery = NearestNeighborsQuery.Exact("vec", Vec.Empty(), Similarity.Jaccard),
          testMapping = Mapping.SparseBool(4096),
          testQueries = Seq(Query(NearestNeighborsQuery.Exact("vec", Vec.Empty(), Similarity.Jaccard), 100))
        ).toBase64,
        resultsBucket = "local",
        s3Minio = true,
        skipExisting = false
      )).exitCode
}

object ExecuteLocalDenseFloat extends App {

  override def run(args: List[String]): URIO[Console, ExitCode] =
    Execute(
      Execute.Params(
        experimentJsonBase64 = Experiment(
          Dataset.RandomDenseFloat(2048, 10000),
          exactMapping = Mapping.DenseFloat(2048),
          exactQuery = NearestNeighborsQuery.Exact("vec", Vec.Empty(), Similarity.Angular),
          testMapping = Mapping.DenseFloat(2048),
          testQueries = Seq(Query(NearestNeighborsQuery.Exact("vec", Vec.Empty(), Similarity.Angular), 100))
        ).toBase64,
        resultsBucket = "local",
        s3Minio = true,
        skipExisting = false
      )).exitCode
}
