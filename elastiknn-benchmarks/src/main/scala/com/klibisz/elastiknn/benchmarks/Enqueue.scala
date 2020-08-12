package com.klibisz.elastiknn.benchmarks

import com.amazonaws.services.s3.model.PutObjectResult
import com.klibisz.elastiknn.benchmarks.codecs._
import io.circe.syntax._
import zio._
import zio.blocking.Blocking
import zio.console._

/**
  * Produce multiple Experiments for downstream processing.
  * Write each experiment to S3 and write the keys to a file, also in s3.
  */
object Enqueue extends App {

  /**
    * Parameters for running the Enqueue App.
    * @param experimentsPrefix Prefix for each generated experiment key.
    * @param keysKey Key of the JSON file containing generated experiment keys.
    * @param bucket Bucket where all benchmarking data is stored.
    * @param s3Url URL for the s3 client to access. Lets you define a custom client, e.g. for minio at http://localhost:9000.
    */
  case class Params(
      experimentsPrefix: String = "",
      keysKey: String = "",
      bucket: String = "",
      s3Url: Option[String] = None
  )

  private val parser = new scopt.OptionParser[Params]("Generate a list of benchmark experiments") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[String]("experimentsPrefix").action((s, c) => c.copy(experimentsPrefix = s))
    opt[String]("keysKey").action((s, c) => c.copy(keysKey = s))
    opt[String]("bucket").action((s, c) => c.copy(bucket = s))
    opt[String]("s3Url").optional().action((s, c) => c.copy(s3Url = Some(s)))
  }

  override def run(args: List[String]): URIO[Console, ExitCode] = parser.parse(args, Params()) match {
    case Some(params) =>
      import params._
      val s3Client = S3Utils.client(s3Url)
      val experiments = Experiment.defaults
      val logic: ZIO[Console with Blocking, Throwable, Unit] = for {
        blocking <- ZIO.access[Blocking](_.get)
        (hashes, effects) = experiments.foldLeft((Vector.empty[String], Vector.empty[Task[PutObjectResult]])) {
          case ((hashes, effects), exp) =>
            val body = exp.asJson.noSpaces
            val hash = exp.md5sum.toLowerCase
            val key = s"${experimentsPrefix}/$hash.json"
            val effect = blocking.effectBlocking(s3Client.putObject(bucket, key, body))
            (hashes :+ hash, effects :+ effect)
        }
        _ <- putStrLn(s"Saving ${hashes.length} experiments to S3")
        _ <- ZIO.collectAll(effects)
        _ <- blocking.effectBlocking(s3Client.putObject(bucket, keysKey, hashes.asJson.noSpaces))
      } yield ()
      val layer = Blocking.live ++ Console.live
      logic.provideLayer(layer).exitCode
    case None => sys.exit(1)
  }
}
