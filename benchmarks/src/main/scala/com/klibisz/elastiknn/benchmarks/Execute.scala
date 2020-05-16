package com.klibisz.elastiknn.benchmarks

import java.util.Base64

import zio._
import zio.console._
import codecs._
import io.circe.parser._

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

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = parser.parse(args, Params()) match {
    case Some(params) =>
      val logic = for {
        jsonString <- ZIO.fromTry(Try(new String(decoder.decode(params.experimentJsonBase64))))
        experiment <- ZIO.fromEither(decode[Experiment](jsonString))
        _ <- putStrLn(experiment.toString)
      } yield ()
      logic
        .mapError(System.err.println)
        .fold(_ => 1, _ => 0)
    case None => sys.exit(1)
  }

}
