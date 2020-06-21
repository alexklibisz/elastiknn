package com.klibisz.elastiknn.benchmarks

import com.klibisz.elastiknn.api.{Mapping, NearestNeighborsQuery, Similarity, Vec}
import zio._
import zio.console.Console

/**
  * App that runs micro-benchmarks in a Github Workflow.
  */
object ContinuousBenchmark extends App {

  private val randomDenseFloats = Dataset.RandomDenseFloat(1000, 50000, 1000)
  private val randomSparseBools = Dataset.RandomSparseBool(3000, 50000, 1000)
  private val field = "vec"
  private val bucket = s"elastiknn-benchmarks"
  private val k = 100

  private val experiments = Seq(
    // L2 exact, LSH
    Experiment(
      randomDenseFloats,
      Mapping.DenseFloat(randomDenseFloats.dims),
      NearestNeighborsQuery.Exact(field, Similarity.L2),
      Mapping.L2Lsh(randomDenseFloats.dims, 400, 1, 3),
      Seq(
        Query(NearestNeighborsQuery.L2Lsh(field, 1000), k),
        Query(NearestNeighborsQuery.L2Lsh(field, 1300, useMLTQuery = true), k)
      )
    ),
    // Angular exact, LSH
    Experiment(
      randomDenseFloats,
      Mapping.DenseFloat(randomDenseFloats.dims),
      NearestNeighborsQuery.Exact(field, Similarity.Angular),
      Mapping.AngularLsh(randomDenseFloats.dims, 400, 1),
      Seq(
        Query(NearestNeighborsQuery.AngularLsh(field, 1000), k),
        Query(NearestNeighborsQuery.AngularLsh(field, 1300, useMLTQuery = true), k),
      )
    ),
    // Jaccard exact, sparse indexed, LSH
    Experiment(
      randomSparseBools,
      Mapping.SparseBool(randomSparseBools.dims),
      NearestNeighborsQuery.Exact(field, Similarity.Jaccard),
      Mapping.JaccardLsh(randomSparseBools.dims, 400, 1),
      Seq(
        Query(NearestNeighborsQuery.JaccardLsh(field, 1000), k),
        Query(NearestNeighborsQuery.JaccardLsh(field, 1300, useMLTQuery = true), k)
      )
    )
  )

  override def run(args: List[String]): URIO[Console, ExitCode] = {
    val s3Client = S3Utils.minioClient()
    val experimentEffects = experiments.map { exp =>
      for {
        _ <- ZIO(s3Client.putObject(bucket, s"experiments/${exp.md5sum}.json", codecs.experimentCodec(exp).noSpaces))
        params = Execute.Params(
          experimentHash = exp.md5sum,
          experimentsBucket = bucket,
          experimentsPrefix = "experiments",
          datasetsBucket = bucket,
          datasetsPrefix = "data/processed",
          resultsBucket = bucket,
          resultsPrefix = "results",
          parallelism = 2,
          s3Minio = true,
          recompute = true
        )
        _ <- Execute(params)
      } yield ()
    }
    val pipeline = for {
      bucketExists <- ZIO(s3Client.doesBucketExistV2(bucket))
      _ <- if (!bucketExists) ZIO(s3Client.createBucket(bucket)) else ZIO.succeed(())
      _ <- ZIO.collectAll(experimentEffects)
      _ <- Aggregate(Aggregate.Params(bucket, "results", bucket, "results/aggregate/aggregate.csv", s3Minio = true))
    } yield ()
    pipeline.exitCode
  }

}
