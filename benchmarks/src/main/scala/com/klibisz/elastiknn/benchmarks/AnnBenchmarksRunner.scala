package com.klibisz.elastiknn.benchmarks

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, LshQueryOptions, QueryOptions}
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.client.ElastiKnnClient
import com.klibisz.elastiknn.utils.ElastiKnnVectorUtils
import com.klibisz.elastiknn.{ExactModelOptions, JaccardLshOptions, ProcessorOptions, Similarity, TestData}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.bulk.BulkResponse
import io.circe
import org.apache.commons.io.FileUtils
import org.apache.commons.math3.stat.descriptive.rank.Percentile
import scalapb_circe.JsonFormat
import zio._
import kantan.csv._
import kantan.csv.ops._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration => ScalaDuration}
import scala.util.{Failure, Success, Try}

object AnnBenchmarksRunner extends ElastiKnnVectorUtils {

  final case class CLIArgs(host: String = "localhost",
                           port: Int = 9200,
                           benchmarkFile: File = new File(getClass.getResource("benchmarks.yaml").toURI),
                           datasets: Seq[String] = Seq("kosarak"),
                           duration: ScalaDuration = ScalaDuration("8 hours"),
                           outputFile: File = new File("benchmark-results.csv"))

  private val cliParser = new scopt.OptionParser[CLIArgs]("benchmark runner") {
    opt[String]('h', "host").action((s, a) => a.copy(host = s))
    opt[Int]('p', "port").action((i, a) => a.copy(port = i))
    opt[String]('i', "benchmarkFile").action((s, a) => a.copy(benchmarkFile = new File(s)))
    opt[Seq[String]]('d', "datasets").action((ss, a) => a.copy(datasets = ss))
    opt[String]('o', "outputFile").action((s, a) => a.copy(outputFile = new File(s)))
  }

  final case class Result(dataset: String,
                          shards: Int,
                          queries: Int,
                          modelOptions: ModelOptions,
                          corpusSize: Int,
                          indexMillisP95: Float,
                          searchMillisP95: Float,
                          queriesPerSecond: Int,
                          aggregateRecall: Float)

  object Result {
    def apply(dataset: String,
              shards: Int,
              queries: Int,
              modelOptions: ModelOptions,
              testData: TestData,
              indexResultsMillis: Seq[(Long, BulkResponse)],
              searchTotalMillis: Long,
              searchRecallMillis: Seq[(Long, Double)]): Result = Result(
      dataset,
      shards,
      queries,
      modelOptions,
      testData.corpus.length,
      new Percentile().evaluate(indexResultsMillis.map(_._1.toDouble).toArray, 0.95).toFloat,
      new Percentile().evaluate(searchRecallMillis.map(_._1.toDouble).toArray, 0.95).toFloat,
      (testData.queries.length / (searchTotalMillis / 1000)).toInt,
      searchRecallMillis.map(_._2).sum.toFloat / searchRecallMillis.length
    )
  }

  private def writeResults(results: Seq[Result], file: File): UIO[Unit] = {
    ZIO.effectTotal(
      file.writeCsv(
        results.map(r =>
          List(
            r.dataset,
            r.shards.toString,
            r.queries.toString,
            r.modelOptions match {
              case ModelOptions.Exact(ex)     => JsonFormat.toJsonString(ex)
              case ModelOptions.Jaccard(jlsh) => JsonFormat.toJsonString(jlsh)
              case ModelOptions.Empty         => ""
            },
            r.corpusSize.toString,
            r.indexMillisP95.toString,
            r.searchMillisP95.toString,
            r.queriesPerSecond.toString,
            r.aggregateRecall.toString
        )),
        rfc.withHeader("dataset",
                       "shards",
                       "queries",
                       "model options",
                       "corpus size",
                       "index millis p95",
                       "search millis p95",
                       "queries per second",
                       "aggregate recall")
      ))
  }

  private val annBenchmarksRoot: File = new File(s"${System.getProperty("user.home")}/.ann-benchmarks")

  private def parseTestData(dataset: String) = dataset.toLowerCase match {
    case "kosarak" =>
      for {
        file <- ZIO.succeed(new File(s"$annBenchmarksRoot/kosarak-jaccard.json"))
        jsonStr <- ZIO.effectTotal(FileUtils.readFileToString(file, StandardCharsets.UTF_8))
        testData <- ZIO.fromEither(io.circe.parser.decode[TestData](jsonStr))
      } yield testData
    case other => ZIO.fail(new IllegalArgumentException(s"Unknown dataset $other"))
  }

  private def interpretParallelism(n: Double): Int = {
    lazy val numProcs = java.lang.Runtime.getRuntime.availableProcessors()
    if (n < 0) numProcs + n.toInt
    else if (n.isValidInt) n.toInt
    else (numProcs * n).floor.toInt
  }

  private def time[T](t: => Task[T]): ZIO[Any, Throwable, (Long, T)] =
    for {
      t0 <- ZIO.effectTotal(System.currentTimeMillis())
      r <- t
      t1 <- ZIO.effectTotal(System.currentTimeMillis())
    } yield (t1 - t0, r)

  private def run(dataset: String, testData: TestData, shards: Int, queries: Int, modelOptions: ModelOptions)(
      implicit client: ElastiKnnClient) = {
    val index = s"benchmark-${UUID.randomUUID}"
    val pipelineId = s"ingest-$index"
    val rawField = "vec_raw"
    val vectorIds = testData.corpus.indices.map(_.toString)
    QueryOptions.Lsh
    val queryOptsTry: Try[QueryOptions] = modelOptions match {
      case ModelOptions.Exact(ex)  => Success(QueryOptions.Exact(ExactQueryOptions(rawField, ex.similarity)))
      case ModelOptions.Jaccard(_) => Success(QueryOptions.Lsh(LshQueryOptions(pipelineId)))
      case _                       => Failure(new IllegalArgumentException("Invalid or empty model options"))
    }
    val dimsTry = testData.corpus.head.dimensions
    for {
      dims <- ZIO.fromTry(dimsTry)
      queryOpts <- ZIO.fromTry(queryOptsTry)
      procOpts = ProcessorOptions(rawField, dims, modelOptions)
      _ <- ZIO.fromFuture { implicit ec =>
        for {
          _ <- client.execute(createIndex(index).shards(shards))
          _ <- client.prepareMapping(index, procOpts)
          _ <- client.createPipeline(pipelineId, procOpts)
        } yield ()
      }
      indexEffects = testData.corpus.zip(vectorIds).grouped(100).toIterable.map { pairs =>
        val (vecs, ids) = (pairs.map(_._1), pairs.map(_._2))
        time(ZIO.fromFuture(_ => client.indexVectors(index, pipelineId, rawField, vecs, Some(ids))))
      }
      indexResultsMillis <- ZIO.collectAll(indexEffects)
      searchEffects = testData.queries.map { q =>
        for {
          (searchMillis, searchRes) <- time(ZIO.fromFuture { _ =>
            client.knnQuery(index, queryOpts, q.vector, q.indices.length, fetchSource = false)
          })
          recall = searchRes.hits.hits.count(h => q.indices.contains(h.id.toInt)) * 1.0 / q.indices.length
        } yield (searchMillis, recall)
      }
      (searchTotalMillis, searchRecallsMillis) <- time(ZIO.collectAllParN(queries)(searchEffects))
      res = Result(dataset, shards, queries, modelOptions, testData, indexResultsMillis, searchTotalMillis, searchRecallsMillis)
      _ <- zio.console.putStrLn(res.toString)
    } yield res
  }

  private def expandSpace(space: ParameterSpace): Seq[ModelOptions] = space match {
    case ParameterSpace.Exact(similarity: String) =>
      Similarity.values
        .find(_.name.toLowerCase == similarity.toLowerCase)
        .map(s => ModelOptions.Exact(ExactModelOptions(s)))
        .toSeq
    case ParameterSpace.JaccardLSH(tables, bands, rows) =>
      for {
        t <- tables
        b <- bands
        r <- rows
      } yield ModelOptions.Jaccard(JaccardLshOptions(0, fieldProcessed = "vec_proc", t, b, r))
  }

  private def runDefinition(bdef: BenchmarkDefinition)(implicit client: ElastiKnnClient) = {
    for {
      testData <- parseTestData(bdef.dataset)
      runs = for {
        s <- bdef.shards
        q <- bdef.queryParallelism
        mm <- bdef.spaces.map(expandSpace)
        m <- mm
      } yield run(bdef.dataset, testData, interpretParallelism(s), interpretParallelism(q), m)
      results <- ZIO.collectAll(runs)
    } yield results
  }

  private def parseDefinitions(file: File, datasets: Seq[String]): ZIO[Any, circe.Error, Seq[BenchmarkDefinition]] =
    for {
      yamlStr <- ZIO.effectTotal(FileUtils.readFileToString(file, StandardCharsets.UTF_8))
      jsonAdt <- ZIO.fromEither(io.circe.yaml.parser.parse(yamlStr))
      defns <- ZIO.fromEither(jsonAdt.as[Seq[BenchmarkDefinition]])
    } yield defns.filter(d => datasets.contains(d.dataset))

  def main(args: Array[String]): Unit = cliParser.parse(args, CLIArgs()) match {
    case Some(cliArgs) =>
      implicit val client: ElastiKnnClient = ElastiKnnClient()(ExecutionContext.global)
      val runtime = new DefaultRuntime {}
      val program = for {
        definitions <- parseDefinitions(cliArgs.benchmarkFile, cliArgs.datasets)
        results <- ZIO.collectAll(definitions.map(runDefinition(_)))
        _ <- writeResults(results.flatten, cliArgs.outputFile)
      } yield ()
      try runtime.unsafeRun(program)
      finally client.close()
    case None => System.exit(0)
  }
}
