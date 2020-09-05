package com.klibisz.elastiknn.benchmarks

import com.klibisz.elastiknn.api._
import zio._
import zio.console.Console

/**
  * App that runs micro-benchmarks in a Github Workflow.
  */
object ContinuousBenchmark extends App {

  private val field = "vec"
  private val bucket = s"elastiknn-benchmarks"
  private val k = 100

  private val experiments = Seq(
    // L2
    Experiment(
      Dataset.AnnbSift,
      Mapping.DenseFloat(Dataset.AnnbSift.dims),
      NearestNeighborsQuery.Exact(field, Similarity.L2),
      Mapping.L2Lsh(Dataset.AnnbSift.dims, 300, 2, 1),
      Seq(
        Query(NearestNeighborsQuery.L2Lsh(field, 4000), k)
      )
    ),
    Experiment(
      Dataset.AnnbSift,
      Mapping.DenseFloat(Dataset.AnnbSift.dims),
      NearestNeighborsQuery.Exact(field, Similarity.L2),
      Mapping.L2Lsh(Dataset.AnnbSift.dims, 100, 2, 1),
      Seq(
        Query(NearestNeighborsQuery.L2Lsh(field, 4000, 3), k)
      )
    ),
    // Angular
    Experiment(
      Dataset.AnnbGlove25,
      Mapping.DenseFloat(Dataset.AnnbGlove25.dims),
      NearestNeighborsQuery.Exact(field, Similarity.Angular),
      Mapping.AngularLsh(Dataset.AnnbGlove25.dims, 60, 3),
      Seq(
        Query(NearestNeighborsQuery.AngularLsh(field, 4000), k)
      )
    ),
    Experiment(
      Dataset.AnnbGlove25,
      Mapping.DenseFloat(Dataset.AnnbGlove25.dims),
      NearestNeighborsQuery.Exact(field, Similarity.Angular),
      Mapping.PermutationLsh(Dataset.AnnbGlove25.dims, 15, repeating = false),
      Seq(
        Query(NearestNeighborsQuery.PermutationLsh(field, Similarity.Angular, 5000), k)
      )
    )
  )

  override def run(args: List[String]): URIO[Console, ExitCode] = {
    val s3Url = "http://localhost:9000"
    val s3Client = S3Utils.client(Some(s3Url))
    val experimentEffects = experiments.map { exp =>
      val key = s"experiments/${exp.md5sum}"
      for {
        _ <- ZIO(s3Client.putObject(bucket, key, codecs.experimentCodec(exp).noSpaces))
        _ <- Execute(
          Execute.Params(
            experimentKey = key,
            datasetsPrefix = "data/processed",
            resultsPrefix = "results",
            bucket = bucket,
            s3Url = Some(s3Url),
            maxQueries = 1000
          ))
      } yield ()
    }
    val pipeline = for {
      bucketExists <- ZIO(s3Client.doesBucketExistV2(bucket))
      _ <- if (!bucketExists) ZIO(s3Client.createBucket(bucket)) else ZIO.succeed(())
      _ <- ZIO.collectAll(experimentEffects)
      _ <- Aggregate(
        Aggregate.Params(
          "results",
          "results/aggregate.csv",
          bucket,
          Some(s3Url),
          Some("https://api.airtable.com/v0/appmy9gAptPsjo4M7/Results"),
          sys.env.get("AIRTABLE_API_KEY")
        ))
    } yield ()
    pipeline.exitCode
  }

}
