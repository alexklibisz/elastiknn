package com.klibisz.elastiknn.benchmarks

import com.klibisz.elastiknn.api.{Mapping, NearestNeighborsQuery, Similarity, Vec}
import zio._
import zio.console.Console

/**
  * App that runs micro-benchmarks in a Github Workflow.
  */
object Micro extends App {

  private val sparseIndexedExp = {
    val dataset = Dataset.RandomSparseBool(1000, 10000)
    Experiment(
      dataset,
      Mapping.SparseIndexed(dataset.dims),
      NearestNeighborsQuery.SparseIndexed("vec", Vec.Empty(), Similarity.Jaccard),
      Mapping.SparseIndexed(dataset.dims),
      Seq(
        Query(NearestNeighborsQuery.SparseIndexed("vec", Vec.Empty(), Similarity.Jaccard), 100)
      )
    )
  }

  private val angularLshExp = {
    val dataset = Dataset.RandomDenseFloat(1000, 10000)
    Experiment(
      dataset,
      Mapping.DenseFloat(dataset.dims),
      NearestNeighborsQuery.Exact("vec", Vec.Empty(), Similarity.Angular),
      Mapping.AngularLsh(dataset.dims, 600, 1),
      Seq(
        Query(NearestNeighborsQuery.AngularLsh("vec", Vec.Empty(), 500), 100)
      )
    )
  }

  override def run(args: List[String]): URIO[Console, ExitCode] = {
    val s3Client = S3Utils.minioClient()
    val exp = angularLshExp
    s3Client.putObject("elastiknn-benchmarks", s"experiments/${exp.md5sum}.json", codecs.experimentCodec(exp).noSpaces)
    Execute(
      Execute.Params(
        experimentHash = exp.md5sum,
        experimentsBucket = "elastiknn-benchmarks",
        experimentsPrefix = "experiments",
        datasetsBucket = "elastiknn-benchmarks",
        datasetsPrefix = "data/processed",
        resultsBucket = "elastiknn-benchmarks",
        resultsPrefix = "results",
        parallelism = 8,
        s3Minio = true,
        skipExisting = false
      )).exitCode
  }

}
