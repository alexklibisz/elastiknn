package com.elastiknn.annb

import com.klibisz.elastiknn.api.Vec

sealed trait Dataset {
  type V <: Vec.KnownDims
  val name: String
  val dims: Int
}

object Dataset {
  // ann-benchmarks: https://github.com/erikbern/ann-benchmarks/blob/master/ann_benchmarks/datasets.py#L429
  // big-ann-benchmarks: https://github.com/harsha-simhadri/big-ann-benchmarks/blob/main/benchmark/datasets.py#L599
  case object FashionMnist extends Dataset {
    type V = Vec.DenseFloat
    val name = "fashion-mnist-784-euclidean"
    val dims = 784
  }
  case object Glove25Angular extends Dataset {
    type V = Vec.DenseFloat
    val name = "glove-25-angular"
    val dims = 25
  }
  val All = Seq(FashionMnist, Glove25Angular)
}
