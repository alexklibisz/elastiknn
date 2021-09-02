package com.elastiknn.annb

import com.klibisz.elastiknn.api.Vec

sealed trait DatasetFormat

object DatasetFormat {
  sealed trait AnnBenchmarks extends DatasetFormat
  sealed trait BigAnnBenchmarks extends DatasetFormat
}

sealed trait Dataset[V <: Vec, F <: DatasetFormat] {
  type V_ = V
  type F_ = F
  val name: String
  val dims: Int
}

object Dataset {
  // ann-benchmarks: https://github.com/erikbern/ann-benchmarks/blob/master/ann_benchmarks/datasets.py#L429
  // big-ann-benchmarks: https://github.com/harsha-simhadri/big-ann-benchmarks/blob/main/benchmark/datasets.py#L599
  import DatasetFormat._
  case object FashionMnist extends Dataset[Vec.DenseFloat, AnnBenchmarks] {
    val name = "fashion-mnist-784-euclidean"
    val dims = 784
  }
  case object Glove25Angular extends Dataset[Vec.DenseFloat, AnnBenchmarks] {
    val name = "glove-25-angular"
    val dims = 25
  }
  val All = Seq(FashionMnist, Glove25Angular)
}
