package com.klibisz.elastiknn.benchmarks

import java.io.File

import kantan.csv._
import kantan.csv.ops._
import zio._
import zio.blocking.Blocking
import zio.console.Console
import zio.logging.log
import zio.logging.slf4j.Slf4jLogger
import zio.stream.ZSink

/**
  * Ingest results and compute pareto curves for each set of results grouped by (dataset, algorithm, k).
  */
object Aggregate extends App {

  final case class Params(resultsPrefix: String = "", aggregateKey: String = "", bucket: String = "", s3Url: Option[String] = None)

  private val parser = new scopt.OptionParser[Params]("Aggregate results into a single file") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[String]("resultsPrefix").action((x, c) => c.copy(resultsPrefix = x)).required()
    opt[String]("aggregateKey").action((x, c) => c.copy(aggregateKey = x)).required()
    opt[String]("bucket").action((x, c) => c.copy(bucket = x)).required()
    opt[String]("s3Url").action((x, c) => c.copy(s3Url = Some(x))).optional()
  }

  implicit object OrderBenchmarkResult extends Ordering[BenchmarkResult] {
    override def compare(x: BenchmarkResult, y: BenchmarkResult): Int =
      Ordering[(String, String, String)].compare(
        (x.dataset.name, x.similarity.toString, x.algorithm),
        (y.dataset.name, y.similarity.toString, y.algorithm)
      )
  }

  implicit object KantanEncoder extends HeaderEncoder[BenchmarkResult] {
    import scala.language.implicitConversions
    import com.klibisz.elastiknn.api.ElasticsearchCodec._
    private implicit def circeLike[T: io.circe.Encoder](t: T): String = implicitly[io.circe.Encoder[T]].apply(t).noSpacesSortKeys
    override def header: Option[Seq[String]] =
      Some(
        Seq(
          "dataset",
          "similarity",
          "algorithm",
          "mapping",
          "query",
          "k",
          "shards",
          "replicas",
          "parallelQueries",
          "esNodes",
          "esCoresPerNode",
          "esMemoryGb",
          "warmupQueries",
          "minWarmupRounds",
          "maxWarmupRounds",
          "recall",
          "queriesPerSecond",
          "durationMillis"
        ))
    override def rowEncoder: RowEncoder[BenchmarkResult] =
      (d: BenchmarkResult) =>
        Seq[String](
          d.dataset.name,
          d.similarity,
          d.algorithm,
          d.mapping,
          d.query,
          d.k,
          d.shards,
          d.replicas,
          d.parallelQueries,
          d.esNodes,
          d.esCoresPerNode,
          d.esMemoryGb,
          d.warmupQueries,
          d.minWarmupRounds,
          d.maxWarmupRounds,
          d.recall,
          d.queriesPerSecond,
          d.durationMillis
      )
  }

  def apply(params: Params): ZIO[Any, Throwable, Unit] = {
    import params._
    val s3Client = S3Utils.client(s3Url)

    val layer =
      (Blocking.live ++ ZLayer.succeed(s3Client)) >>>
        Slf4jLogger.make((_, s) => s, Some(this.getClass.getSimpleName)) ++
          ResultClient.s3(bucket, params.resultsPrefix) ++
          Blocking.live

    val logic = for {
      resultClient <- ZIO.access[Has[ResultClient]](_.get)
      blocking <- ZIO.access[Blocking](_.get)

      // Stream results from S3.
      results = resultClient.all()
      rows <- results.run(ZSink.collectAll).map(_.sorted)

      // Write rows to CSV and upload them to S3.
      csvFile = File.createTempFile("tmp", ".csv")
      writer = csvFile.asCsvWriter[BenchmarkResult](rfc.withHeader)
      _ = rows.foreach(writer.write)
      _ = writer.close()
      _ <- log.info(s"Wrote ${rows.length} rows to csv file.")
      _ <- blocking.effectBlocking(s3Client.putObject(bucket, params.aggregateKey, csvFile))

    } yield ()

    logic.provideLayer(layer)
  }

  override def run(args: List[String]): URIO[Any with Console, ExitCode] = parser.parse(args, Params()) match {
    case Some(params) => apply(params).exitCode
    case None         => sys.exit(1)
  }
}
