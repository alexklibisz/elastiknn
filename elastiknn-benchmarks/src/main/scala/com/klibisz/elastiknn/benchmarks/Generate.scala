package com.klibisz.elastiknn.benchmarks

import java.io.File
import java.nio.file.Files

import com.amazonaws.services.s3.model.PutObjectResult
import com.klibisz.elastiknn.api.{Mapping, NearestNeighborsQuery, Similarity}
import com.klibisz.elastiknn.benchmarks.codecs._
import io.circe.Encoder
import io.circe.generic.semiauto
import io.circe.syntax._
import zio._
import zio.blocking.Blocking
import zio.console._

/**
  * Produce multiple Experiments for downstream processing. Write each experiment to S3.
  * Write a local file, where each line contains an experiment key and the number of shards required by that experiment.
  */
object Generate extends App {

  /**
    * Parameters for running the Enqueue App.
    * See parser for descriptions of each parameter.
    */
  case class Params(experimentsPrefix: String = "", manifestPath: String = "", bucket: String = "", s3Url: Option[String] = None)

  private val parser = new scopt.OptionParser[Params]("Generate a list of benchmark experiments") {
    override def showUsageOnError: Option[Boolean] = Some(true)
    help("help")
    opt[String]("experimentsPrefix")
      .text("s3 prefix where experiments are stored")
      .action((s, c) => c.copy(experimentsPrefix = s))
      .required()
    opt[String]("bucket")
      .text("bucket for all s3 data")
      .action((s, c) => c.copy(bucket = s))
      .required()
    opt[String]("s3Url")
      .text("URL accessed by the s3 client")
      .action((s, c) => c.copy(s3Url = Some(s)))
      .optional()
    opt[String]("manifestPath")
      .text("local path containing information needed to setup infrastructure for each generated experiment")
      .action((s, c) => c.copy(manifestPath = s))
      .required()
  }

  private case class ArgoBenchmarkStepParams(experimentKey: String,
                                             esClusterName: String,
                                             esNodeCount: Int,
                                             esCoreCountPerNode: Int,
                                             esMemGB: Int,
                                             driverCoreCount: Int)
  private implicit val encoder: Encoder[ArgoBenchmarkStepParams] = semiauto.deriveEncoder[ArgoBenchmarkStepParams]

  private val vecName: String = "vec"

  /**
    * Returns reasonable Experiments for doing a gridsearch over parameters for the given dataset.
    */
  def gridsearch(dataset: Dataset, shards: Seq[Int] = Seq(1)): Seq[Experiment] = dataset match {

    case Dataset.AnnbMnist | Dataset.AnnbFashionMnist =>
      val exact = shards.map { s =>
        Experiment(
          dataset,
          Mapping.DenseFloat(dataset.dims),
          Seq(Query(NearestNeighborsQuery.Exact(vecName, Similarity.L2), 100)),
          shards = s
        )
      }
      val lsh = for {
        tables <- Seq(50, 75, 100)
        hashesPerTable <- Seq(2, 3, 4)
        width <- 5 to 8
        s <- shards
      } yield
        Experiment(
          dataset,
          Mapping.L2Lsh(dataset.dims, L = tables, k = hashesPerTable, w = width),
          for {
            candidates <- Seq(1000, 5000)
            probes <- Seq(0, 3, 6, 9)
          } yield Query(NearestNeighborsQuery.L2Lsh(vecName, candidates / s, probes), 100),
          shards = s
        )
      (exact ++ lsh).flatMap { exp =>
        Seq(exp, exp.copy(shards = 3, replicas = 2, esNodes = 3, esCoresPerNode = 3, esMemoryGb = 4, parallelQueries = 10))
      }

    case Dataset.AnnbSift =>
      val exact = Seq(
        Experiment(
          dataset,
          Mapping.DenseFloat(dataset.dims),
          Seq(Query(NearestNeighborsQuery.Exact(vecName, Similarity.L2), 100))
        ))
      val lsh = for {
        tables <- Seq(50, 75, 100)
        hashesPerTable <- Seq(2, 3, 4)
        width <- Seq(1, 2, 3)
      } yield
        Experiment(
          dataset,
          Mapping.L2Lsh(dataset.dims, L = tables, k = hashesPerTable, w = width),
          for {
            candidates <- Seq(1000, 5000, 10000)
            probes <- 0 to math.pow(hashesPerTable, 3).toInt.min(9) by 3
          } yield Query(NearestNeighborsQuery.L2Lsh(vecName, candidates, probes), 100)
        )
      exact ++ lsh

    case Dataset.AnnbGlove100 =>
      val exact = Seq(
        Experiment(dataset, Mapping.DenseFloat(dataset.dims), Seq(Query(NearestNeighborsQuery.Exact(vecName, Similarity.Angular), 100)))
      )
      val projections = for {
        tables <- Seq(50, 100, 125)
        hashesPerTable <- Seq(6, 9)
      } yield
        Experiment(
          dataset,
          Mapping.AngularLsh(dataset.dims, tables, hashesPerTable),
          for {
            candidates <- Seq(1000, 5000)
          } yield Query(NearestNeighborsQuery.AngularLsh(vecName, candidates), 100)
        )
      val permutations = for {
        k <- Seq(10, 25, 50, 75)
        rep <- Seq(false)
      } yield
        Experiment(
          dataset,
          Mapping.PermutationLsh(dataset.dims, k, repeating = rep),
          for {
            candidates <- Seq(1000, 5000, 10000)
          } yield Query(NearestNeighborsQuery.PermutationLsh(vecName, Similarity.Angular, candidates), 100)
        )
      exact ++ projections ++ permutations

    case _ => Seq.empty
  }

  override def run(args: List[String]): URIO[Console, ExitCode] = parser.parse(args, Params()) match {
    case Some(params) =>
      import params._
      val s3Client = S3Utils.client(s3Url)
      val experiments = gridsearch(Dataset.AnnbFashionMnist, Seq(1, 4, 8))
      val logic: ZIO[Console with Blocking, Throwable, Unit] = for {
        _ <- putStrLn(s"Saving ${experiments.length} experiments to S3")
        blocking <- ZIO.access[Blocking](_.get)
        (outputs, effects) = experiments.foldLeft((Vector.empty[ArgoBenchmarkStepParams], Vector.empty[Task[PutObjectResult]])) {
          case ((outputs, effects), exp) =>
            import exp._
            val body = exp.asJson.noSpaces
            val hash = exp.md5sum.toLowerCase
            val key = s"$experimentsPrefix/$hash"
            val output = ArgoBenchmarkStepParams(key, hash, esNodes, esCoresPerNode, esMemoryGb, math.max(parallelQueries / 2, 1))
            val effect = blocking.effectBlocking(s3Client.putObject(bucket, key, body))
            (outputs :+ output, effects :+ effect)
        }
        _ <- ZIO.collectAllParN(16)(effects)
        _ <- blocking.effectBlocking(Files.writeString(new File(manifestPath).toPath, outputs.asJson.noSpaces))
      } yield ()
      val layer = Blocking.live ++ Console.live
      logic.provideLayer(layer).exitCode
    case None => sys.exit(1)
  }
}
