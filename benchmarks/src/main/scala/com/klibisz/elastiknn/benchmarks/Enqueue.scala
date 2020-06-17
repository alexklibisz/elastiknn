package com.klibisz.elastiknn.benchmarks

import java.io.File
import java.nio.file.Files

import com.klibisz.elastiknn.benchmarks.codecs._
import io.circe.syntax._
import org.apache.commons.codec.digest.DigestUtils
import zio._
import zio.blocking.Blocking
import zio.console._

import scala.util.Random

/**
  * Produce a list of Experiments for downstream processing.
  * Write each experiment to S3 and write the keys to a file.
  * Each key gets passed onto an argo workflow job.
  */
object Enqueue extends App {

  case class Params(datasetsFilter: Set[String] = Set.empty,
                    file: File = new File("/tmp/hashes.txt"),
                    experimentsBucket: String = "",
                    experimentsPrefix: String = "",
                    s3Minio: Boolean = false)

  private val parser = new scopt.OptionParser[Params]("Build a list of benchmark jobs") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[Seq[String]]("datasetsFilter")
      .unbounded()
      .action((s, c) => c.copy(datasetsFilter = s.map(_.toLowerCase).toSet))
    opt[String]("experimentsBucket")
      .action((x, c) => c.copy(experimentsBucket = x))
    opt[String]("experimentsPrefix")
      .action((x, c) => c.copy(experimentsPrefix = x))
    opt[String]("file")
      .action((s, c) => c.copy(file = new File(s)))
    opt[Boolean]("s3Minio")
      .action((x, c) => c.copy(s3Minio = x))
  }

  override def run(args: List[String]): URIO[Console, ExitCode] = parser.parse(args, Params()) match {
    case Some(params) =>
      val experiments =
        if (params.datasetsFilter.isEmpty) Experiment.defaults
        else Experiment.defaults.filter(e => params.datasetsFilter.contains(e.dataset.name.toLowerCase))
      val s3Client = if (params.s3Minio) S3Utils.minioClient() else S3Utils.defaultClient()
      val layer = Blocking.live ++ Console.live
      val logic: ZIO[Console with Blocking, Throwable, Unit] = for {
        blocking <- ZIO.access[Blocking](_.get)
        hashesAndEffects = experiments.map { exp =>
          val body = exp.asJson.noSpaces
          val hash = DigestUtils.md5Hex(body).toLowerCase
          val key = s"${params.experimentsPrefix}/$hash.json"
          hash -> blocking.effectBlocking(s3Client.putObject(params.experimentsBucket, key, body))
        }
        _ <- putStrLn(s"Saving ${hashesAndEffects.length} experiments to S3")
        _ <- ZIO.collectAllParN(10)(hashesAndEffects.map(_._2))
        jsonListOfHashes = new Random(0).shuffle(hashesAndEffects).map(_._1).asJson.noSpaces
        _ <- blocking.effectBlocking(Files.writeString(params.file.toPath, jsonListOfHashes))
      } yield ()
      logic.provideLayer(layer).exitCode
    case None => sys.exit(1)
  }
}
