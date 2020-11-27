package com.klibisz.elastiknn.benchmarks

import java.net.URI

import com.amazonaws.services.s3.AmazonS3
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.benchmarks.codecs._
import io.circe.parser._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console._
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.stream._

import scala.util.hashing.MurmurHash3

/**
  * Executes a single experiment containing one exact mapping, one test mapping, and many test queries.
  */
object Execute extends App {

  final case class Params(experimentKey: String = "",
                          datasetsPrefix: String = "",
                          resultsPrefix: String = "",
                          recompute: Boolean = false,
                          bucket: String = "",
                          s3Url: Option[String] = None,
                          esUrl: String = "http://localhost:9200",
                          numQueries: Int = 10000,
                          warmupQueries: Int = 200,
                          minWarmupRounds: Int = 10,
                          maxWarmupRounds: Int = 100)

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
      .action((s, c) => c.copy(esUrl = s))
      .optional()
    opt[Int]("numQueries")
      .text("number of queries to execute")
      .action((i, c) => c.copy(numQueries = i))
      .optional()
    opt[Int]("warmupQueries")
      .text("number of queries to execute in the warmup phase")
      .action((i, c) => c.copy(warmupQueries = i))
      .optional()
    opt[Int]("minWarmupRounds")
      .text("run warmup queries at least this many times")
      .action((i, c) => c.copy(minWarmupRounds = i))
      .optional()
    opt[Int]("maxWarmupRounds")
      .text("run warmup queries at most this many times, or stop after the speed has stopped improving")
      .action((i, c) => c.copy(maxWarmupRounds = i))
      .optional()
  }

  private def readExperiment(bucket: String, key: String) =
    for {
      blocking <- ZIO.access[Blocking](_.get)
      s3Client <- ZIO.access[Has[AmazonS3]](_.get)
      body <- blocking.effectBlocking(s3Client.getObjectAsString(bucket, key))
      exp <- ZIO.fromEither(decode[Experiment](body))
    } yield exp

  private def indexAndSearch(
      dataset: Dataset,
      eknnMapping: Mapping,
      eknnQuery: NearestNeighborsQuery,
      k: Int,
      shards: Int,
      numQueries: Int,
      warmupQueries: Int,
      minWarmupRounds: Int,
      maxWarmupRounds: Int
  ) = {

    // Index name is a function of dataset, mapping and holdout so we can check if it already exists and avoid re-indexing.
    val index = s"ix-${dataset.name}-${MurmurHash3.orderedHash(Seq(dataset, eknnMapping, shards))}".toLowerCase

    for {

      searchClient <- ZIO.access[Has[SearchClient]](_.get)
      datasetClient <- ZIO.access[Has[DatasetClient]](_.get)

      // Check if the index already exists.
      _ <- log.info(s"Checking for index $index with mapping $eknnMapping")
      exists <- searchClient.indexExists(index)

      // Create the index if doesn't exist.
      _ <- if (exists) log.info(s"Found index [$index]")
      else
        for {
          _ <- log.info(s"Creating index [$index] with mapping [$eknnMapping] and [$shards] shards")
          (dur, n) <- searchClient.buildIndex(index, eknnMapping, shards, datasetClient.streamTrain(dataset)).timed
          _ <- log.info(s"Indexed [$n] vectors in [${dur.getSeconds}] seconds")
        } yield ()

      // Warmup. Run up to maxWarmupRounds, or until the duration stops decreasing.
      warmupQueries <- datasetClient.streamTest(dataset, Some(warmupQueries)).map(eknnQuery.withVec).run(ZSink.collectAll)
      warmupSearches = searchClient.search(index, Stream.fromChunk(warmupQueries), k)
      _ <- ZIO.iterate((0, Vector.empty[Long])) {
        case (r, dd) =>
          r < minWarmupRounds || (r < maxWarmupRounds && ((dd.length < 2) || (dd.takeRight(2) != dd.takeRight(2).sorted)))
      } {
        case (round, dd) =>
          for {
            (dur, _) <- warmupSearches.run(ZSink.drain).timed
            _ <- log.info(s"Completed warmup [$round] of [$maxWarmupRounds] in [${dur.toMillis}] ms")
          } yield (round + 1, dd :+ dur.toMillis)
      }

      // Actual benchmark.
      _ <- log.info(s"Completed warmup and starting benchmark")
      queryStream = datasetClient.streamTest(dataset, Some(numQueries)).map(eknnQuery.withVec)
      resultsStream = searchClient.search(index, queryStream, k)
      (dur, results) <- resultsStream.run(ZSink.collectAll).map(_.toArray).timed
      _ <- log.info(s"Completed [${results.length}] searches in [${dur.toMillis / 1000f}] seconds")
      distances <- datasetClient.streamDistances(dataset).run(ZSink.collectAll).map(_.toArray)
    } yield
      BenchmarkResult(
        dataset = dataset,
        mapping = eknnMapping,
        query = eknnQuery,
        k = k,
        shards = shards,
        durationMillis = dur.toMillis,
        queriesPerSecond = results.length * 1f / dur.toSeconds,
        queryResults = results.zip(distances).map {
          case (res, dists) =>
            val lowerBound = dists.min
            val gteq = res.scores.count(_ >= lowerBound)
            res.copy(recall = gteq * 1f / res.scores.length)
        }
      )
  }

  private def run(experiment: Experiment, numQueries: Int, warmupQueries: Int, minWarmupRounds: Int, maxWarmupRounds: Int) = {
    import experiment._
    for {
      resultsClient <- ZIO.access[Has[ResultClient]](_.get)
      testEffects = experiment.queries.map {
        case Query(query, k) =>
          for {
            resultOpt <- resultsClient.find(dataset, mapping, query, k, shards)
            _ <- log.locally(LogAnnotation.Name(List(mapping, query).map(_.toString))) {
              resultOpt match {
                case Some(_) => log.info(s"Found test result")
                case _ =>
                  for {
                    result <- indexAndSearch(dataset,
                                             mapping,
                                             query,
                                             k,
                                             shards,
                                             numQueries,
                                             warmupQueries,
                                             minWarmupRounds,
                                             maxWarmupRounds)
                    _ <- resultsClient.save(result)
                  } yield ()
              }
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
    val searchClientLayer = {
      val timeoutMillis = 10 * 60 * 1000 // Set timeout ridiculously high to account for merging segments.
      SearchClient.elasticsearch(URI.create(esUrl), strictFailure = true, timeoutMillis = timeoutMillis)
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
      experiment <- readExperiment(bucket, experimentKey)
      _ <- log.info(s"Running experiment: $experiment")

      // Wait for cluster ready.
      _ <- log.info("Waiting for cluster")
      searchBackend <- ZIO.access[Has[SearchClient]](_.get)
      _ <- searchBackend.blockUntilReady()

      // Run the experiment.
      _ <- run(experiment, numQueries, warmupQueries, minWarmupRounds, maxWarmupRounds)

    } yield ()

    steps.provideLayer(layer)
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = parser.parse(args, Params()) match {
    case Some(params) => apply(params).exitCode
    case None         => sys.exit(1)
  }

}
