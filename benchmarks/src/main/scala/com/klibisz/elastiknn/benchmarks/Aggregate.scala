package com.klibisz.elastiknn.benchmarks

import java.io.File

import com.klibisz.elastiknn.api.Mapping._
import com.klibisz.elastiknn.api._
import kantan.csv._
import kantan.csv.ops._
import kantan.csv.generic._
import org.apache.commons.math3.stat.descriptive.rank.Percentile
import zio._
import zio.blocking.Blocking
import zio.console.Console
import zio.logging.log
import zio.logging.slf4j.Slf4jLogger

/**
  * Ingest results and compute pareto curves for each set of results grouped by (dataset, algorithm, k).
  */
object Aggregate extends App {

  final case class Params(resultsBucket: String = "",
                          resultsPrefix: String = "",
                          resultsS3URL: String = "",
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

  final case class CsvRow(dataset: String,
                          algorithm: String,
                          k: Int,
                          mappingJson: String,
                          queryJson: String,
                          recallP10: Double,
                          recallP50: Double,
                          recallP90: Double,
                          durationP10: Double,
                          durationP50: Double,
                          durationP90: Double)

  private val header = Seq("dataset",
                           "algorithm",
                           "k",
                           "mappingJson",
                           "queryJson",
                           "recallP10",
                           "recallP50",
                           "recallP90",
                           "durationP10",
                           "durationP50",
                           "durationP90")

  private def mappingToAlgorithmName(m: Mapping): String = m match {
    case _: SparseBool                                            => "exact"
    case _: DenseFloat                                            => "exact"
    case _: SparseIndexed                                         => "sparse indexed"
    case _: JaccardLsh | _: HammingLsh | _: AngularLsh | _: L2Lsh => "lsh"
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
      rows = results.mapMPar(10) { res =>
        val recalls = res.queryResults.map(_.recall).toArray
        val durations = res.queryResults.map(_.duration.toDouble).toArray
        val ptile = new Percentile()
        val row = CsvRow(
          res.dataset.name,
          mappingToAlgorithmName(res.mapping),
          res.k,
          ElasticsearchCodec.encode(res.mapping).noSpaces,
          ElasticsearchCodec.encode(res.query).noSpaces,
          ptile.evaluate(recalls, 0.1),
          ptile.evaluate(recalls, 0.5),
          ptile.evaluate(recalls, 0.9),
          ptile.evaluate(durations, 0.1),
          ptile.evaluate(durations, 0.5),
          ptile.evaluate(durations, 0.9),
        )
        log.info(row.toString).map(_ => row)
      }

      // Write the rows to a temporary file
      csvFile = File.createTempFile("tmp", ".csv")
      writer = csvFile.asCsvWriter[CsvRow](rfc.withHeader(header: _*))
      count <- rows.fold(0) {
        case (n, row) =>
          writer.write(row)
          n + 1
      }
      _ <- log.info(s"Wrote $count rows to csv file.")
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
