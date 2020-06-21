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

  final case class Params(resultsBucket: String = "",
                          resultsPrefix: String = "",
                          aggregateBucket: String = "",
                          aggregateKey: String = "",
                          s3Minio: Boolean = false)

  private val parser = new scopt.OptionParser[Params]("Aggregate results into a single file") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[String]("resultsBucket").action((x, c) => c.copy(resultsBucket = x))
    opt[String]("resultsPrefix").action((x, c) => c.copy(resultsPrefix = x))
    opt[String]("aggregateBucket").action((x, c) => c.copy(aggregateBucket = x))
    opt[String]("aggregateKey").action((x, c) => c.copy(aggregateKey = x))
    opt[Boolean]("s3Minio").action((x, c) => c.copy(s3Minio = x))
  }

  def apply(params: Params): ZIO[Any, Throwable, Unit] = {
    val s3Client = if (params.s3Minio) S3Utils.minioClient() else S3Utils.defaultClient()
    val layer =
      (Blocking.live ++ ZLayer.succeed(s3Client)) >>>
        Slf4jLogger.make((_, s) => s, Some(getClass.getSimpleName)) ++
          ResultClient.s3(params.resultsBucket, params.resultsPrefix) ++
          Blocking.live

    val logic = for {
      // Stream results from S3.
      resultClient <- ZIO.access[ResultClient](_.get)
      results = resultClient.all()

      // Transform them to rows.
      aggStream = results
        .mapMPar(10) { res =>
          val agg = AggregateResult(res)
          log.info(agg.toString).map(_ => agg)
        }

      rows <- aggStream.run(ZSink.collectAll).map(_.sortBy(a => (a.dataset, a.similarity, a.algorithm)))

      // Write the rows to a temporary file
      csvFile = File.createTempFile("tmp", ".csv")
      writer = csvFile.asCsvWriter[AggregateResult](rfc.withHeader(AggregateResult.header: _*))
      _ = rows.foreach(writer.write)
      _ <- log.info(s"Wrote ${rows.length} rows to csv file.")
      _ = writer.close()

      // Upload the file.
      blocking <- ZIO.access[Blocking](_.get)
      _ <- blocking.effectBlocking(s3Client.putObject(params.aggregateBucket, params.aggregateKey, csvFile))

    } yield ()

    logic.provideLayer(layer)
  }

  override def run(args: List[String]): URIO[Any with Console, ExitCode] = parser.parse(args, Params()) match {
    case Some(params) => apply(params).exitCode
    case None         => sys.exit(1)
  }
}
