package com.klibisz.elastiknn.benchmarks

import com.amazonaws.services.s3.model.PutObjectResult
import com.klibisz.elastiknn.benchmarks.codecs._
import io.circe.syntax._
import zio._
import zio.blocking.Blocking
import zio.console._

import scala.util.Random

/**
  * Produce multiple Experiments for downstream processing.
  * Write each experiment to S3 and write the keys to a file, also in s3.
  */
object Generate extends App {

  /**
    * Parameters for running the Enqueue App.
    * See parser for descriptions of each parameter.
    */
  case class Params(experimentsPrefix: String = "", keysKey: String = "", bucket: String = "", s3Url: Option[String] = None)

  private val parser = new scopt.OptionParser[Params]("Generate a list of benchmark experiments") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[String]("experimentsPrefix")
      .text("s3 prefix where experiments are stored")
      .action((s, c) => c.copy(experimentsPrefix = s))
      .required()
    opt[String]("keysKey")
      .text("s3 key where JSON file containing generated experiment keys is stored")
      .action((s, c) => c.copy(keysKey = s))
      .required()
    opt[String]("bucket")
      .text("bucket for all s3 data")
      .action((s, c) => c.copy(bucket = s))
      .required()
    opt[String]("s3Url")
      .text("URL accessed by the s3 client")
      .action((s, c) => c.copy(s3Url = Some(s)))
      .optional()
  }

  override def run(args: List[String]): URIO[Console, ExitCode] = parser.parse(args, Params()) match {
    case Some(params) =>
      import params._
      val s3Client = S3Utils.client(s3Url)
      val experiments = Experiment.gridsearch(Dataset.AnnbGlove100)
      val logic: ZIO[Console with Blocking, Throwable, Unit] = for {
        _ <- putStrLn(s"Saving ${experiments.length} experiments to S3")
        blocking <- ZIO.access[Blocking](_.get)
        (keys, effects) = experiments.foldLeft((Vector.empty[String], Vector.empty[Task[PutObjectResult]])) {
          case ((keys, effects), exp) =>
            val body = exp.asJson.noSpaces
            val hash = exp.md5sum.toLowerCase
            val key = s"$experimentsPrefix/$hash"
            val effect = blocking.effectBlocking(s3Client.putObject(bucket, key, body))
            (keys :+ key, effects :+ effect)
        }
        _ <- ZIO.collectAllParN(16)(effects)
        // Shuffle the keys so that you don't get several pods running exact search for the same dataset simultaneously.
        keysShuffled = new Random(0).shuffle(keys)
        _ <- blocking.effectBlocking(s3Client.putObject(bucket, keysKey, keysShuffled.asJson.noSpaces))
      } yield ()
      val layer = Blocking.live ++ Console.live
      logic.provideLayer(layer).exitCode
    case None => sys.exit(1)
  }
}
