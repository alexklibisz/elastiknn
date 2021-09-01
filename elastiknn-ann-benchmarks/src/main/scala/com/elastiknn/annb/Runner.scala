package com.elastiknn.annb

import akka.actor.ActorSystem
import com.klibisz.elastiknn.api.Vec
import io.circe.Json
import scopt.OptionParser

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

/**
  * big-ann-benchmarks run_from_cmdline: https://github.com/harsha-simhadri/big-ann-benchmarks/blob/004924700184fd79a27ff1a74c675f92dbf271fa/benchmark/runner.py#L114
  * ann-benchmarks run_from_cmdline: https://github.com/erikbern/ann-benchmarks/blob/ef2f85f6ae5891d812d5df1c9db7f4d6b2d087bc/ann_benchmarks/runner.py#L151
  */
object Runner {

  final case class Params(
      dataset: Dataset,
      algo: Algorithm,
      count: Int,
      rebuild: Boolean,
      runs: Int,
      buildArgs: Json,
      queryArgs: List[Json]
  )

  private val defaultParams = Params(
    Dataset.FashionMnist,
    Algorithm.ElastiknnL2Lsh,
    rebuild = false,
    count = 10,
    runs = 1,
    buildArgs = Json.Null,
    queryArgs = List.empty
  )

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

  def main(args: Array[String]): Unit = optionParser.parse(args, defaultParams) match {
    case None => sys.exit(1)
    case Some(params) =>
      implicit val ec: ExecutionContext = ExecutionContext.global
      implicit val sys: ActorSystem = ActorSystem()
      try {
        val config = RunnerConfig.configured
        val client = DatasetClient(params.dataset, config.datasetsPath)
        val example = client
          .indexVectors()
          .runForeach(v => println(v.asInstanceOf[Vec.DenseFloat].values.max, v.asInstanceOf[Vec.DenseFloat].values.length))
        Await.result(example, Duration.Inf)

        // Setup the results client.
        // Setup the Lucene algorithm client.
        // Build the index, via Lucene algo client.
        // Run the queries, keeping results in memory, via Lucene algo client.
        // Flush the results to disk.
      } finally sys.terminate()
  }

}
