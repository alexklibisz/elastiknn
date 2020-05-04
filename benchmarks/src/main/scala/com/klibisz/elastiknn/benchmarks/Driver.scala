package com.klibisz.elastiknn.benchmarks

import java.io.File

import com.klibisz.elastiknn.api._
import zio._
import zio.console._

import scala.concurrent.duration.Duration
import scala.util

object Driver extends App {

  case class Options(datasetsDirectory: File = new File(s"${System.getProperty("user.home")}/.elastiknn-data"),
                     elasticsearchHost: String = "http://localhost",
                     elasticsearchPort: Int = 9200,
                     resultsFile: File = new File("/tmp/results.json"),
                     ks: Seq[Int] = Seq(10, 100),
                     testQueries: Int = 1000)

  private val optionParser = new scopt.OptionParser[Options]("Benchmarks driver") {
    opt[String]('d', "datasetsDirectory").action((s, c) => c.copy(datasetsDirectory = new File(s)))
    opt[String]('h', "elasticsearchHost").action((s, c) => c.copy(elasticsearchHost = s))
    opt[Int]('p', "elasticsearchPort").action((i, c) => c.copy(elasticsearchPort = i))
    opt[String]('o', "resultsFile").action((s, c) => c.copy(resultsFile = new File(s)))
    opt[Int]('q', "testQueries").action((i, c) => c.copy(testQueries = i))
  }

  private val vectorField: String = "vec"
  private val querySelectionSeed: Long = 0

  private case class SearchResult(id: Int, neighborIds: Vector[Int], duration: Duration)

  private def recalls(exact: Vector[SearchResult], test: Vector[SearchResult]): Seq[Double] = exact.zip(test).map {
    case (ex, ts) => ex.neighborIds.intersect(ts.neighborIds).length * 1d / ex.neighborIds.length
  }

  private def runSingle(dataset: Dataset,
                        mapping: Mapping,
                        mkQuery: (String, Vec, Int) => NearestNeighborsQuery,
                        k: Int,
                        testQueries: Int): ZIO[ElastiknnZioClient with DatasetClient, Nothing, Vector[SearchResult]] =
    // Index the dataset, hold out `testQueries` vectors to use as queries, run the queries, return one SearchResult for each query.
    ZIO.succeed(Vector.empty)

  private def runExperiments(experiments: Seq[Experiment],
                             ks: Seq[Int],
                             testQueries: Int = 1000): ZIO[DatasetClient with ResultClient with ElastiknnZioClient, Throwable, Unit] =
    for {
      resultClient: ResultClient.Service <- ZIO.access[ResultClient](_.get)
      _ <- ZIO.collectAll(
        for {
          exp <- experiments
          k <- ks
        } yield
          for {
            exactResults <- runSingle(exp.dataset, exp.exact.mapping, exp.exact.mkQuery.head, k, testQueries)
            _ <- ZIO.collectAll(for {
              maq <- exp.maqs
              mkQuery <- maq.mkQuery
              emptyResult = Result(exp.dataset, exp.exact.mapping, mkQuery(vectorField, Vec.Empty(), k), k, Seq.empty, Seq.empty)
            } yield
              for {
                found <- resultClient.find(emptyResult.dataset, emptyResult.mapping, emptyResult.query, emptyResult.k)
                _ <- if (found.isDefined) ZIO.succeed(())
                else
                  for {
                    testResults <- runSingle(exp.dataset, maq.mapping, mkQuery, k, testQueries)
                    populatedResult: Result = emptyResult.copy(durations = testResults.map(_.duration.toMillis),
                                                               recalls = recalls(exactResults, testResults))
                    _ <- resultClient.save(populatedResult)
                  } yield ()
              } yield ())
          } yield ()
      )
    } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    optionParser.parse(args, Options()) match {
      case None => sys.exit(1)
      case Some(opts) =>
        val layer: ZLayer[Any, Throwable, Console with DatasetClient with ResultClient with ElastiknnZioClient] =
          Console.live ++ DatasetClient.local(opts.datasetsDirectory) ++ ResultClient.local(opts.resultsFile) ++
            ElastiknnZioClient.fromFutureClient(opts.elasticsearchHost, opts.elasticsearchPort, strictFailure = true)
        runExperiments(Experiment.defaults, opts.ks, opts.testQueries)
          .provideLayer(layer)
          .mapError(System.err.println)
          .fold(_ => 1, _ => 0)
    }
  }
}
