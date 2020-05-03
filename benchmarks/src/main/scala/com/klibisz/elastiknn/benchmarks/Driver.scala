package com.klibisz.elastiknn.benchmarks

import java.io.File

import com.klibisz.elastiknn.api._
import zio._
import zio.console._

object Driver extends App {

  case class Options(datasetsDirectory: File = new File(s"${System.getProperty("user.home")}/.elastiknn-data"),
                     elasticsearchUrl: String = "http://localhost:9200",
                     resultsFile: File = new File("/tmp/results.json"))

  private val optionParser = new scopt.OptionParser[Options]("Benchmarks driver") {
    opt[String]('d', "datasetsDirectory")
      .action((s, c) => c.copy(datasetsDirectory = new File(s)))
    opt[String]('e', "elasticsearchUrl")
      .action((s, c) => c.copy(elasticsearchUrl = s))
    opt[String]('o', "resultsFile")
      .action((s, c) => c.copy(resultsFile = new File(s)))
  }

  private val logic: ZIO[Console with DatasetClient, Throwable, Unit] = for {
    datasetClient: DatasetClient.Service <- ZIO.access[DatasetClient](_.get)
    s = datasetClient.streamVectors[Vec.SparseBool](Dataset.AmazonHomePhash)
    _ <- s.foreach(v => putStrLn(v.toString))
  } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    optionParser.parse(args, Options()) match {
      case None => sys.exit(1)
      case Some(opts) =>
        val layer: ZLayer[Any, Throwable, Console with DatasetClient] = Console.live ++ DatasetClient.local(opts.datasetsDirectory)
        logic.provideLayer(layer).mapError(System.err.println).fold(_ => 1, _ => 0)
    }
  }
}
