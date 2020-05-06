package com.klibisz.elastiknn.benchmarks

import java.io.File

import com.klibisz.elastiknn.api._
import zio._
import zio.console._

import scala.concurrent.duration.Duration
import scala.util

object Driver extends App {

  case class Options(datasetsDirectory: File = new File(s"${System.getProperty("user.home")}/.elastiknn-data"),
                     elasticsearchHost: String = "localhost",
                     elasticsearchPort: Int = 9200,
                     resultsFile: File = new File("/tmp/results.json"),
                     ks: Seq[Int] = Seq(10, 100),
                     holdoutProportion: Double = 0.1)

  private val optionParser = new scopt.OptionParser[Options]("Benchmarks driver") {
    opt[String]('d', "datasetsDirectory").action((s, c) => c.copy(datasetsDirectory = new File(s)))
    opt[String]('h', "elasticsearchHost").action((s, c) => c.copy(elasticsearchHost = s))
    opt[Int]('p', "elasticsearchPort").action((i, c) => c.copy(elasticsearchPort = i))
    opt[String]('o', "resultsFile").action((s, c) => c.copy(resultsFile = new File(s)))
    opt[Double]('h', "holdoutPercentage").action((d, c) => c.copy(holdoutProportion = d))
  }

  private val vectorField: String = "vec"
  private val holdoutSeed: Long = 0

  private case class SearchResult(id: Int, neighborIds: Vector[Int], duration: Duration)

  private def recalls(exact: Seq[SearchResult], test: Seq[SearchResult]): Seq[Double] = exact.zip(test).map {
    case (ex, ts) => ex.neighborIds.intersect(ts.neighborIds).length * 1d / ex.neighborIds.length
  }

  private def buildIndex(mapping: Mapping,
                         shards: Int,
                         dataset: Dataset,
                         holdoutProportion: Double): ZIO[ElastiknnZioClient with DatasetClient, Throwable, (String, Vector[Vec])] = {
    import com.sksamuel.elastic4s.ElasticDsl.{mapping => m, _}
    val indexName = s"benchmark-${dataset.name}"
    for {
      eknnClient <- ZIO.access[ElastiknnZioClient](_.get)
      _ <- eknnClient.execute(createIndex(indexName).shards(shards))
      _ <- eknnClient.putMapping(indexName, vectorField, mapping)
    } yield (indexName, Vector.empty)
  }

  private def searchIndex(index: String,
                          query: NearestNeighborsQuery,
                          holdoutVectors: Vector[Vec],
                          k: Int): ZIO[ElastiknnZioClient with DatasetClient, Nothing, Seq[SearchResult]] =
    // Index the dataset, hold out `testQueries` vectors to use as queries, run the queries, return one SearchResult for each query.
    ZIO.succeed(Vector.empty)

  private def deleteIndex(index: String): Unit = {
    ???
  }

  private def run(experiments: Seq[Experiment],
                  ks: Seq[Int],
                  holdoutProportion: Double): ZIO[ElastiknnZioClient with DatasetClient with ResultClient, Throwable, List[Unit]] =
    ZIO.foreach(experiments) { exp =>
      for {
        (index, holdoutVectors) <- buildIndex(exp.exact.mapping, exp.shards, exp.dataset, holdoutProportion)
        _ <- ZIO.foreach(ks) { k =>
          for {
            exactResults <- searchIndex(index, exp.exact.mkQuery.head(vectorField, holdoutVectors.head, k), holdoutVectors, k)
            testRuns = for {
              maq <- exp.maqs
              mkQuery <- maq.mkQuery
              emptyQuery = mkQuery(vectorField, Vec.Empty(), k)
              emptyResult = Result(exp.dataset, maq.mapping, emptyQuery, k, Seq.empty, Seq.empty)
            } yield
              for {
                resultClient: ResultClient.Service <- ZIO.access[ResultClient](_.get)
                found <- resultClient.find(emptyResult.dataset, emptyResult.mapping, emptyResult.query, emptyResult.k)
                _ <- if (found.isDefined) ZIO.succeed(())
                else
                  for {
                    (index, _) <- buildIndex(maq.mapping, exp.shards, exp.dataset, holdoutProportion)
                    testResults <- searchIndex(index, emptyQuery, holdoutVectors, k)
                    populatedResult: Result = emptyResult.copy(durations = testResults.map(_.duration.toMillis),
                                                               recalls = recalls(exactResults, testResults))
                    _ <- resultClient.save(populatedResult)
                  } yield ()
              } yield ()
            _ <- ZIO.collectAll(testRuns)
          } yield ()
        }
      } yield ()
    }

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    optionParser.parse(args, Options()) match {
      case None => sys.exit(1)
      case Some(opts) =>
        val layer: ZLayer[Any, Throwable, Console with DatasetClient with ResultClient with ElastiknnZioClient] =
          Console.live ++ DatasetClient.local(opts.datasetsDirectory) ++ ResultClient.local(opts.resultsFile) ++
            ElastiknnZioClient.fromFutureClient(opts.elasticsearchHost, opts.elasticsearchPort, strictFailure = true)
        run(Experiment.defaults, opts.ks, opts.holdoutProportion)
          .provideLayer(layer)
          .mapError(System.err.println)
          .fold(_ => 1, _ => 0)
    }
  }
}
