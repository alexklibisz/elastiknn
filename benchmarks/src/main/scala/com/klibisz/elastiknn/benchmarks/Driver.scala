package com.klibisz.elastiknn.benchmarks

import java.io.File

import com.klibisz.elastiknn.api._
import zio._
import zio.console._

object Driver extends App {

  case class Options(datasetsDirectory: File = new File(s"${System.getProperty("user.home")}/.elastiknn-data"),
                     elasticsearchHost: String = "http://localhost",
                     elasticsearchPort: Int = 9200,
                     resultsFile: File = new File("/tmp/results.json"))

  private val optionParser = new scopt.OptionParser[Options]("Benchmarks driver") {
    opt[String]('d', "datasetsDirectory").action((s, c) => c.copy(datasetsDirectory = new File(s)))
    opt[String]('h', "elasticsearchHost").action((s, c) => c.copy(elasticsearchHost = s))
    opt[Int]('p', "elasticsearchPort").action((i, c) => c.copy(elasticsearchPort = i))
    opt[String]('o', "resultsFile").action((s, c) => c.copy(resultsFile = new File(s)))
  }

  private val logic: ZIO[Console with DatasetClient with ResultClient with ElastiknnZioClient, Throwable, Unit] = for {
    datasetClient: DatasetClient.Service <- ZIO.access[DatasetClient](_.get)
    resultClient: ResultClient.Service <- ZIO.access[ResultClient](_.get)
    eknnClient: ElastiknnZioClient.Service <- ZIO.access[ElastiknnZioClient](_.get)
    s = datasetClient.streamVectors[Vec.SparseBool](Dataset.AmazonHomePhash)
    _ <- s.take(10).zipWithIndex.foreach {
      case (v, i) => putStrLn(s"$i: $v")
    }
    fakeResult = Result(Dataset.AmazonHomePhash,
                        Mapping.SparseBool(4096),
                        NearestNeighborsQuery.Exact("", Vec.Empty(), Similarity.Hamming),
                        20,
                        Seq.empty,
                        Seq.empty)
    _ <- resultClient.save(fakeResult)
    find <- resultClient.find(fakeResult.dataset, fakeResult.mapping, fakeResult.query, fakeResult.k)
    _ <- putStrLn(find.toString)
    all <- resultClient.all
    _ <- ZIO.collectAll(all.map(r => putStrLn(r.toString)))
  } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    optionParser.parse(args, Options()) match {
      case None => sys.exit(1)
      case Some(opts) =>
        val layer: ZLayer[Any, Throwable, Console with DatasetClient with ResultClient with ElastiknnZioClient] =
          Console.live ++ DatasetClient.local(opts.datasetsDirectory) ++ ResultClient.local(opts.resultsFile) ++
            ElastiknnZioClient.fromFutureClient(opts.elasticsearchHost, opts.elasticsearchPort, true)
        logic.provideLayer(layer).mapError(System.err.println).fold(_ => 1, _ => 0)
    }
  }
}
