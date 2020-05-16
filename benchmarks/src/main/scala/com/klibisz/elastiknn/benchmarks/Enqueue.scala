package com.klibisz.elastiknn.benchmarks

import zio._
import zio.console._
import io.circe.syntax._
import codecs._

object Enqueue extends App {

  case class Params()

  private val parser = new scopt.OptionParser[Params]("Build a list of benchmark jobs") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
  }

  private def expand(experiments: Seq[Experiment] = Experiment.defaults): Seq[Experiment] =
    for {
      exp <- experiments
      maq <- exp.maqs
    } yield exp.copy(maqs = Seq(maq))

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = parser.parse(args, Params()) match {
    case None => sys.exit(1)
    case Some(_) =>
      ZIO
        .collectAll(expand(Seq(Experiment.hamming(Dataset.AmazonHomePhash))).map(exp => putStrLn(exp.asJson.noSpacesSortKeys)))
        .map(_ => 0)
  }
}
