package com.elastiknn.annb

sealed trait DatasetFormat

object DatasetFormat {
  sealed trait AnnBenchmarks extends DatasetFormat
  sealed trait BigAnnBenchmarks extends DatasetFormat
}

sealed abstract class Dataset(val name: String, val dims: Int) { this: DatasetFormat => }

object Dataset {
  // ann-benchmarks: https://github.com/erikbern/ann-benchmarks/blob/master/ann_benchmarks/datasets.py#L429
  // big-ann-benchmarks: https://github.com/harsha-simhadri/big-ann-benchmarks/blob/main/benchmark/datasets.py#L599
  import DatasetFormat._
  case object FashionMnist extends Dataset("fashion-mnist-784-euclidean", 784) with AnnBenchmarks
  val All = Seq(FashionMnist)
}
