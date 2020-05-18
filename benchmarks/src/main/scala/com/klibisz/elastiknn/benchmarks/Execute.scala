package com.klibisz.elastiknn.benchmarks

import java.util.Base64

import zio._
import zio.console._
import codecs._
import io.circe.parser._
import zio.duration.Duration
import scala.concurrent.duration._

import scala.util.Try

object Execute extends App {

  case class Params(experimentJsonBase64: String = "")

  private val parser = new scopt.OptionParser[Params]("Execute benchmark jobs") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[String]("experimentJsonBase64")
      .text("Experiment case class serialized as a Json string")
      .action((s, c) => c.copy(experimentJsonBase64 = s))
      .required()
  }

  private val decoder = Base64.getDecoder

  private def decodeExperiment(jsonBase64: String): IO[Throwable, Experiment] =
    for {
      jsonString <- ZIO.fromTry(Try(new String(decoder.decode(jsonBase64))))
      experiment <- ZIO.fromEither(decode[Experiment](jsonString))
    } yield experiment

  /*

  Logic:
  - Get experiment with a single mapping and many queries.
  - Use s3 results client to check if results exist for _all_ of the queries. If so, we're done.
  - Otherwise proceed.
  - Create index with given mapping.
  - Stream dataset from S3 and index it.
  - Run exact search to get ground truth results.
  - Run all of the queries which don't yet have results.

   */

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = parser.parse(args, Params()) match {
    case Some(params) =>
      val logic = for {
        _ <- putStrLn(params.experimentJsonBase64)
        experiment <- decodeExperiment(params.experimentJsonBase64)
        _ <- putStrLn(experiment.toString)
        _ <- ZIO.sleep(Duration.fromScala(100.seconds))
      } yield ()
      logic
        .mapError(System.err.println)
        .fold(_ => 1, _ => 0)
    case None => sys.exit(1)
  }

}
