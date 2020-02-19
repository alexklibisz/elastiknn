package com.klibisz.elastiknn.benchmarks

import java.io.{File, FileReader}
import java.net.URL
import java.util.UUID

import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, LshQueryOptions, QueryOptions}
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.client.ElastiKnnClient
import com.klibisz.elastiknn.utils.{ElastiKnnVectorUtils, FutureUtils}
import com.klibisz.elastiknn.{JaccardLshOptions, ProcessorOptions, TestData}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.decode
import io.circe.yaml
import org.apache.commons.math3.stat.descriptive.rank.Percentile

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object Runner extends LazyLogging with ElastiKnnVectorUtils with FutureUtils {

  final case class CLIArgs(host: String = "localhost",
                           port: Int = 9200,
                           benchmarkFile: URL = getClass.getResource("benchmarks.yaml"),
                           datasets: Seq[String] = Seq("kosarak"),
                           duration: Duration = Duration("8 hours"))

  private val cliParser = new scopt.OptionParser[CLIArgs]("benchmark runner") {
    opt[String]('h', "host").action((s, a) => a.copy(host = s))
    opt[Int]('p', "port").action((i, a) => a.copy(port = i))
    opt[String]('f', "file").action((s, a) => a.copy(benchmarkFile = new URL(s)))
    opt[Seq[String]]('d', "datasets").action((ss, a) => a.copy(datasets = ss))
  }

  final case class Result(indexMillisPerVector: Double, indexMillisP95: Double, searchMillisPerVector: Double, searchMillisP95: Double)

  private def time[T](f: => Future[T])(implicit ec: ExecutionContext): Future[(T, Int)] =
    for {
      t <- Future.successful(System.currentTimeMillis())
      r <- f
    } yield (r, (System.currentTimeMillis() - t).toInt)

  private def paramsToModelOpts(space: ParameterSpace): Seq[ModelOptions] = space match {
    case ParameterSpace.JaccardLSH(tables, bands, rows) =>
      for {
        t <- tables
        b <- bands
        r <- rows
      } yield ModelOptions.Jaccard(JaccardLshOptions(0, fieldProcessed = "vec_proc", t, b, r))
  }

  private def convertRelativeShardsQueriesToAbsolute(n: Double): Int = {
    val numProcs = Runtime.getRuntime.availableProcessors
    if (n < 0) numProcs + n.toInt
    else if (n.toInt.toDouble != n) (numProcs * n).floor.toInt
    else n.toInt
  }

  private def apply(testData: TestData, shards: Double, queries: Double, modelOptions: ModelOptions)(
      implicit ec: ExecutionContext,
      client: ElastiKnnClient): Future[Result] = {
    val index = s"benchmark-${UUID.randomUUID}"
    val pipeline = s"process-$index"
    val rawField = "vec_raw"
    val queryOpts = modelOptions match {
      case ModelOptions.Exact(ex)  => QueryOptions.Exact(ExactQueryOptions(rawField, ex.similarity))
      case ModelOptions.Jaccard(_) => QueryOptions.Lsh(LshQueryOptions(pipeline))
      case _                       => ???
    }
    val numShards = convertRelativeShardsQueriesToAbsolute(shards)
    val numQueries = convertRelativeShardsQueriesToAbsolute(queries)
    val ids = testData.corpus.indices.map(_.toString)
    for {
      dim <- Future.fromTry(testData.corpus.head.dimensions)
      _ <- client.execute(createIndex(index).shards(numShards))
      _ <- client.createPipeline(pipeline, ProcessorOptions(rawField, dim, modelOptions))
      (indexResultsTimes, indexTotalTime) <- time {
        Future.traverse(testData.corpus.zip(ids).grouped(100)) { pairs =>
          val (vecs, ids) = (pairs.map(_._1), pairs.map(_._2))
          time(client.indexVectors(index, pipeline, rawField, vecs, Some(ids), RefreshPolicy.IMMEDIATE))
        }
      }
      res = Result(
        indexTotalTime / testData.corpus.length.toDouble,
        new Percentile().evaluate(indexResultsTimes.map(_._2.toDouble).toArray, 0.95).toInt,
        0,
        0
      )
//      (searchResultsTimes, searchTotalTime) <- time {
//        this.pipeline(testData.queries, numQueries) { query =>
//          time(client.knnQuery(index, queryOpts, query.vector, query.similarities.length))
//        }
//      }
      _ = logger.info(res.toString)
    } yield res
  }

  private def apply(bdef: BenchmarkDefinition)(implicit ec: ExecutionContext, client: ElastiKnnClient): Future[Seq[Result]] =
    parseTestData(bdef.dataset) match {
      case Success(testData) =>
        // This hacky setup makes sure they run one at a time.
        val runs: Seq[() => Future[Result]] = for {
          shards <- bdef.shards
          queries <- bdef.queryParallelism
          modelOpts <- paramsToModelOpts(bdef.space)
        } yield () => apply(testData, shards, queries, modelOpts)
        Future.traverse(runs)(run => run())
      case Failure(t) => Future.failed(t)
    }

  private val annBenchmarksRoot: File = new File(s"${System.getProperty("user.home")}/.ann-benchmarks")

  private def parseTestData(dataset: String): Try[TestData] = dataset match {
    case "kosarak" =>
      val src = scala.io.Source.fromFile(s"$annBenchmarksRoot/kosarak-jaccard.json")
      val raw = try src.mkString
      finally src.close()
      io.circe.parser.decode[TestData](raw).toTry
    case other => Failure(new IllegalArgumentException(s"Unknown dataset: $other"))
  }

  private def parseDefinitions(cliArgs: CLIArgs): Try[Seq[BenchmarkDefinition]] =
    (for {
      parsed <- yaml.parser.parse(new FileReader(cliArgs.benchmarkFile.getFile))
      decoded <- decode[Seq[BenchmarkDefinition]](parsed.noSpaces)
      matching = decoded.filter(d => cliArgs.datasets.contains(d.dataset))
    } yield matching).toTry

  def main(args: Array[String]): Unit = cliParser.parse(args, CLIArgs()) match {
    case Some(cliArgs) =>
      implicit val ec: ExecutionContext = ExecutionContext.global
      implicit val client: ElastiKnnClient = ElastiKnnClient()
      lazy val pipeline = for {
        bdefs <- Future.fromTry(parseDefinitions(cliArgs))
        _ = logger.info(s"Parsed ${bdefs.length} benchmark definitions:\n ${bdefs.mkString("\n")}")
        _ <- Future.traverse(bdefs)(apply)
      } yield ()
      try Await.result(pipeline, Duration.Inf)
      finally client.close()
    case None => System.exit(1)
  }

}
