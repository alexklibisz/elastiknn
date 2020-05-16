package com.klibisz.elastiknn.benchmarks

import zio._
import zio.console._
import io.circe.syntax._
import codecs._

object Enqueue extends App {

  case class Params(datasetsFilter: Set[String])

  private val parser = new scopt.OptionParser[Params]("Build a list of benchmark jobs") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[Seq[String]]("datasetsFilter")
      .text("List of dataset names that should be included. If empty (the default), all datasets are included.")
      .action((s, c) => c.copy(datasetsFilter = s.map(_.toLowerCase).toSet))
  }

  private def expand(experiments: Seq[Experiment] = Experiment.defaults): Seq[Experiment] =
    for {
      exp <- experiments
      maq <- exp.maqs
    } yield exp.copy(maqs = Seq(maq))

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = parser.parse(args, Params(Set.empty)) match {
    case None => sys.exit(1)
    case Some(params) =>
      val experiments =
        if (params.datasetsFilter.isEmpty) Experiment.defaults
        else Experiment.defaults.filter(e => params.datasetsFilter.contains(e.dataset.name.toLowerCase))
      ZIO
        .collectAll(expand(experiments).map(exp => putStrLn(exp.asJson.noSpacesSortKeys)))
        .map(_ => 0)
  }
}
