package com.elastiknn.annb

import akka.actor.ActorSystem
import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.L2LshModel
import io.circe.{Decoder, Json}
import scopt.OptionParser

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object Runner {

  final case class Params(
      dataset: Dataset[_ <: Benchmark, _ <: Vec.KnownDims],
      algo: Algorithm,
      count: Int,
      rebuild: Boolean,
      runs: Int,
      buildArgs: Json,
      queryArgs: List[Json]
  ) {
    override def toString: String =
      s"Params(dataset=${dataset.name}, algo=${algo.name}, count=$count, rebuild=$rebuild, runs=$runs, buildArgs=${buildArgs.noSpacesSortKeys}, queryArgs=${queryArgs
        .map(_.noSpacesSortKeys)})"
  }

  private val defaultParams = Params(
    Dataset.FashionMnist,
    Algorithm.ElastiknnL2Lsh,
    rebuild = false,
    count = 10,
    runs = 1,
    buildArgs = Json.Null,
    queryArgs = List.empty
  )

  // big-ann-benchmarks run_from_cmdline: https://github.com/harsha-simhadri/big-ann-benchmarks/blob/004924700184fd79a27ff1a74c675f92dbf271fa/benchmark/runner.py#L114
  // ann-benchmarks run_from_cmdline: https://github.com/erikbern/ann-benchmarks/blob/ef2f85f6ae5891d812d5df1c9db7f4d6b2d087bc/ann_benchmarks/runner.py#L151
  private val optionParser = new OptionParser[Params]("(big-)ann-benchmarks CLI") {
    import io.circe.parser
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[String]("dataset")
      .text("Dataset to benchmark on.")
      .required()
      .validate(s =>
        Dataset.All.find(_.name == s) match {
          case None    => Left(s"Invalid algorithm [$s]. Should be one of: ${Dataset.All.map(_.name).mkString(", ")}")
          case Some(_) => Right(())
        }
      )
      .action((s, c) => c.copy(Dataset.All.find(_.name == s).get))
    opt[String]("algorithm")
      .text("Name of algorithm for saving the results.")
      .required()
      .validate(s =>
        Algorithm.All.find(_.name == s) match {
          case None    => Left(s"Invalid algorithm [$s]. Should be one of: ${Algorithm.All.map(_.name).mkString(", ")}")
          case Some(_) => Right(())
        }
      )
      .action((s, c) => c.copy(algo = Algorithm.All.find(_.name == s).get))
    opt[Int]("count")
      .text("Number of nearest neighbors for the algorithm to return.")
      .action((i, c) => c.copy(count = i))
    opt[Int]("runs")
      .text("Number of times to run the algorithm. Will use the fastest run-time over the bunch.")
      .action((i, c) => c.copy(runs = i))
    opt[Unit]("rebuild")
      .text("Re-build the index, even if it exists.")
      .action((_, c) => c.copy(rebuild = true))
    opt[String]("constructor")
      .text("No-op. Used for compatibility (big-)ann-benchmarks frameworks.")
    opt[String]("module")
      .text("No-op. Used for compatibility (big-)ann-benchmarks frameworks.")
    arg[String]("build")
      .text("JSON of arguments to pass to the constructor. E.g. [\"angular\", 100].")
      .validate(s =>
        parser.parse(s) match {
          case Left(err) => Left(s"Invalid json [$s]. Parsing failed with error [${err.message}]")
          case Right(_)  => Right(())
        }
      )
      .action((s, c) => c.copy(buildArgs = parser.parse(s).fold(throw _, identity)))
    arg[String]("queries")
      .text("JSON of arguments to pass to the queries. E.g. [100].")
      .unbounded()
      .validate(s =>
        parser.parse(s) match {
          case Left(err) => Left(s"Invalid json [$s]. Parsing failed with error [${err.message}]")
          case Right(_)  => Right(())
        }
      )
      .action((s, c) => c.copy(queryArgs = c.queryArgs :+ parser.parse(s).fold(throw _, identity)))
  }

  def apply[V <: Vec.KnownDims](client: DatasetClient[V], algo: LuceneAlgorithm[V], params: Params, config: AppConfig): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val sys: ActorSystem = ActorSystem()
    try {
      sys.log.info(s"Running with params [$params] and config [$config]")
      val indexPath = config.indexPath.resolve(params.dataset.name).resolve(params.hashCode().toString).resolve(config.hashCode().toString)
      val example = client
        .indexVectors()
        .zipWithIndex
        .map {
          case (vec, i) =>
            if (i % 10000 == 0) sys.log.info(s"Indexing vector $i")
            algo.toDocument(i + 1, vec)
        }
        .runWith(LuceneSink.store(indexPath, 8))
      Await.result(example, Duration.Inf)
    } finally sys.terminate()
  }

  def apply(params: Params, config: AppConfig): Unit = {
    import params._
    val buildDecoder = Decoder[List[Int]]
    val rng0 = new java.util.Random(0)
    (dataset, algo, buildDecoder.decodeJson(buildArgs)) match {
      case (d: Dataset.AnnBenchmarksDenseFloat, Algorithm.ElastiknnL2Lsh, Right(List(l, k, w))) =>
        apply(
          new DatasetClient.AnnBenchmarksDenseFloat(d, config.datasetsPath),
          new LuceneAlgorithm.ElastiknnDenseFloatHashing(new L2LshModel(d.dims, l, k, w, rng0)),
          params,
          config
        )
      case (_, _, decoded) =>
        throw new IllegalArgumentException(
          s"Unexpected combination of dataset [${dataset.name}], algorithm [${algo.name}], build args [$buildArgs], decoded build args [$decoded]."
        )
    }
  }

  def main(args: Array[String]): Unit = optionParser.parse(args, defaultParams).foreach(apply(_, AppConfig.typesafe))
}
