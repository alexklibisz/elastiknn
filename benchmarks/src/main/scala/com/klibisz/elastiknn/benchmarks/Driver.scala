package com.klibisz.elastiknn.benchmarks

import java.io.File
import java.nio.file.Paths

import com.klibisz.elastiknn.api.{Mapping, NearestNeighborsQuery, Similarity, Vec}
import com.klibisz.elastiknn.client.ElastiknnFutureClient
import zio._
import zio.console._

// Requirements
// - Elastiknn client
// - Dataset storage
// - Results storage

object Driver extends App {

  case class Config(datasetsDirectory: File = new File(s"${System.getProperty("user.home")}/.elastiknn-data"),
                    elasticsearchUrl: String = "http://localhost:9200",
                    resultsFile: File = new File("/tmp/results.json"))

  private val optionParser = new scopt.OptionParser[Config]("Benchmarks driver") {
    opt[String]('d', "datasetsDirectory")
      .action((s, c) => c.copy(datasetsDirectory = new File(s)))
    opt[String]('e', "elasticsearchUrl")
      .action((s, c) => c.copy(elasticsearchUrl = s))
    opt[String]('o', "resultsFile")
      .action((s, c) => c.copy(resultsFile = new File(s)))
  }

  def elastiknnEffect(): ZIO[ElastiknnFutureClient, Throwable, Unit] = ???

  type DatasetClient

  def datasetEffect(): ZIO[DatasetClient, Throwable, Unit] = ???

  type ResultClient

  def resultEffect(): ZIO[ResultClient, Throwable, Unit] = ???

  private val logic: ZIO[ResultClient with DatasetClient with ElastiknnFutureClient, Throwable, Unit] = for {
    eknnClient <- ZIO.access[ElastiknnFutureClient](identity)

    _ <- elastiknnEffect()
    _ <- datasetEffect()
    _ <- resultEffect()
  } yield ()

//  for {
//    _ <- elastiknnEffect()
//    _ <- datasetEffect()
//    _ <- resultEffect()
//  } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = ???

//  private def program(config: Config) = logic.provideSome[Console] { c =>
//    new Console with XModule.Live {
//      override val console: Console.Service[Any] =
//      override val xInstance: XModule.X = XModule.X()
//    }
//  }
//
//  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
//    optionParser.parse(args, Config()) match {
//      case None => sys.exit(1)
//      case Some(config) => program(config)
//    }
//
//    val storage = FileStorage.default
//    val stream = storage.streamDataset[Vec.SparseBool](Dataset.AmazonHomePhash)
//    stream
//      .foreach(v => putStrLn(v.toString))
//      .mapError(System.err.println)
//      .fold(_ => 1, _ => 0)
//
//    storage
//      .saveResult(
//        Result(
//          Dataset.AmazonHomePhash,
//          Mapping.SparseBool(4096),
//          NearestNeighborsQuery.Exact("vec", Vec.Indexed("dummy", "dummy", "dummy"), Similarity.Hamming),
//          10,
//          Seq.empty,
//          Seq.empty
//        ))
//      .fold(_ => 1, _ => 0)
//  }
}
