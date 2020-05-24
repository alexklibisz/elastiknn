package com.klibisz.elastiknn.benchmarks

import java.io.File
import java.nio.file.Files
import java.util.Base64

import zio._
import zio.console._
import io.circe.syntax._
import codecs._

object Enqueue extends App {

  case class Params(datasetsFilter: Set[String] = Set.empty, toFile: Option[File] = None)

  private val parser = new scopt.OptionParser[Params]("Build a list of benchmark jobs") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[Seq[String]]("datasetsFilter")
      .text("List of dataset names that should be included. If empty (the default), all datasets are included.")
      .action((s, c) => c.copy(datasetsFilter = s.map(_.toLowerCase).toSet))
    opt[String]("toFile")
      .text("Optional file where outputs should be written. Otherwise stdout.")
      .action((s, c) => c.copy(toFile = Some(new File(s))))
  }

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = parser.parse(args, Params()) match {
    case None => sys.exit(1)
    case Some(params) =>
      def write(experiments: Seq[Experiment]): ZIO[Console, Throwable, Unit] = {
        val encoder = Base64.getEncoder
        val jsonString = experiments.map(_.asJson.noSpaces.getBytes).map(encoder.encodeToString).asJson.noSpaces
        params.toFile match {
          case Some(f) =>
            for {
              _ <- putStrLn(s"Writing ${experiments.length} expeirments to ${f.getAbsolutePath}")
              _ <- ZIO(Files.writeString(f.toPath, jsonString))
            } yield ()
          case None => putStrLn(jsonString)
        }
      }
      val experiments =
        if (params.datasetsFilter.isEmpty) Experiment.defaults
        else Experiment.defaults.filter(e => params.datasetsFilter.contains(e.dataset.name.toLowerCase))
      write(experiments.take(1))
        .mapError(System.err.println)
        .fold(_ => 1, _ => 0)
  }
}
