package com.klibisz.elastiknn.benchmarks

import java.net.URI

import com.amazonaws.services.s3.AmazonS3
import com.klibisz.elastiknn.api.Similarity
import com.klibisz.elastiknn.benchmarks.codecs._
import io.circe.parser._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console._
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.stream._

/**
  * Executes a single experiment containing one exact mapping, one test mapping, and many test queries.
  */
object Execute extends App {

  final case class Params(
      experimentKey: String = "",
      datasetsPrefix: String = "",
      resultsPrefix: String = "",
      recompute: Boolean = false,
      bucket: String = "",
      s3Url: Option[String] = None,
      esUrl: String = "http://localhost:9200"
  )

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
  }

  private def readExperiment(bucket: String, key: String) =
    for {
      blocking <- ZIO.access[Blocking](_.get)
      s3Client <- ZIO.access[Has[AmazonS3]](_.get)
      body <- blocking.effectBlocking(s3Client.getObjectAsString(bucket, key))
      exp <- ZIO.fromEither(decode[Experiment](body))
    } yield exp

  implicit class ExperimentSyntax(exp: Experiment) {
    // This index name is the UUID of the experiment with all properties which don't matter for indexing zeroed out.
    val index: String =
      exp.copy(queries = Seq.empty, minWarmupRounds = 0, maxWarmupRounds = 0, warmupQueries = 0, parallelQueries = 0).uuid
  }

  private def index(experiment: Experiment) =
    for {
      searchClient <- ZIO.access[Has[SearchClient]](_.get)
      indexExists <- searchClient.indexExists(experiment.index)
      _ <- if (indexExists) ZIO.succeed(())
      else
        for {
          datasetClient <- ZIO.access[Has[DatasetClient]](_.get)
          corpusStream = datasetClient.streamTrain(experiment.dataset)
          _ <- searchClient.buildIndex(experiment.index, experiment.mapping, experiment.shards, corpusStream)
        } yield ()
    } yield ()

  private def warmup(experiment: Experiment, query: Query) = {
    import experiment._
    for {
      _ <- log.info(s"Starting warmup for query [$query]")
      searchClient <- ZIO.access[Has[SearchClient]](_.get)
      datasetClient <- ZIO.access[Has[DatasetClient]](_.get)
      warmupQueries <- datasetClient.streamTest(experiment.dataset).take(experiment.warmupQueries).map(query.nnq.withVec).runCollect
      warmupSearches = searchClient.search(experiment.index, Stream.fromChunk(warmupQueries), query.k, 1)
      _ <- ZIO.iterate((0, Vector.empty[Long])) {
        case (r, dd) =>
          r < minWarmupRounds || (r < maxWarmupRounds && ((dd.length < 2) || (dd.takeRight(2) != dd
            .takeRight(2)
            .sorted)))
      } {
        case (round, dd) =>
          for {
            (dur, _) <- warmupSearches.run(ZSink.drain).timed
            _ <- log.info(s"Completed warmup [$round] of [$maxWarmupRounds] in [${dur.toMillis}] ms")
          } yield (round + 1, dd :+ dur.toMillis)
      }
      _ <- log.info(s"Completed warmup")
    } yield ()
  }

  private def search(experiment: Experiment, query: Query) =
    for {
      _ <- log.info(s"Starting query [${query}]")
      searchClient <- ZIO.access[Has[SearchClient]](_.get)
      datasetClient <- ZIO.access[Has[DatasetClient]](_.get)
      distances <- datasetClient.streamDistances(experiment.dataset).runCollect
      queryStream = datasetClient.streamTest(experiment.dataset).map(query.nnq.withVec)
      resultsStream = searchClient.search(experiment.index, queryStream, query.k, experiment.parallelQueries)
      (dur, results) <- resultsStream.runCollect.timed
      _ <- log.info(s"Completed [${results.length}] searches in [${dur.toMillis / 1000f}] seconds")
    } yield {
      // Same method for computing recall as ann-benchmarks.
      def lowerBound(dists: Seq[Float]): Double = query.nnq.similarity match {
        case Similarity.L2     => dists.map(d => 1 / (1 + d)).min
        case Similarity.Cosine => dists.map(2 - _).min
        case _                 => Double.MaxValue
      }

      val recalls = results
        .zip(distances)
        .map {
          case (res, dists) =>
            val lb = lowerBound(dists)
            val gteq = res.scores.count(_ >= lb)
            gteq * 1f / res.scores.length
        }

      BenchmarkResult(
        dataset = experiment.dataset,
        similarity = query.nnq.similarity,
        algorithm = query.algorithmName,
        mapping = experiment.mapping,
        query = query.nnq,
        k = query.k,
        shards = experiment.shards,
        replicas = experiment.replicas,
        parallelQueries = experiment.parallelQueries,
        esNodes = experiment.esNodes,
        esCoresPerNode = experiment.esCoresPerNode,
        esMemoryGb = experiment.esMemoryGb,
        warmupQueries = experiment.warmupQueries,
        minWarmupRounds = experiment.minWarmupRounds,
        maxWarmupRounds = experiment.maxWarmupRounds,
        recall = recalls.sum / results.length,
        queriesPerSecond = results.length * 1f / dur.toSeconds,
        durationMillis = dur.toMillis
      )
    }

  private def run(experiment: Experiment) =
    for {
      _ <- log.info(s"Starting experiment [$experiment]")
      resultsClient <- ZIO.access[Has[ResultClient]](_.get)
      searchClient <- ZIO.access[Has[SearchClient]](_.get)
      missingQueries <- ZIO.foldLeft(experiment.queries)(Vector.empty[Query]) {
        case (acc, query) => resultsClient.find(experiment, query).map(_.fold(acc :+ query)(_ => acc))
      }
      _ <- log.info(s"Found results for [${experiment.queries.length - missingQueries.length}] of [${experiment.queries.length}] queries.")
      _ <- if (missingQueries.isEmpty) ZIO.succeed(())
      else searchClient.blockUntilReady().flatMap(_ => index(experiment))
      _ <- ZIO.foreach(missingQueries) { query =>
        warmup(experiment, query)
          .flatMap(_ => search(experiment, query))
          .flatMap(resultsClient.save(experiment, query, _))
      }
    } yield ()

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
      _ <- log.info(params.toString)
      experiment <- readExperiment(bucket, experimentKey)
      _ <- run(experiment)
    } yield ()

    steps.provideLayer(layer)
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = parser.parse(args, Params()) match {
    case Some(params) => apply(params).exitCode
    case None         => sys.exit(1)
  }

}
