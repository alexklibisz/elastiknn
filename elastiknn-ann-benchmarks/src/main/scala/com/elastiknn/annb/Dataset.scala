package com.elastiknn.annb

import com.klibisz.elastiknn.api.Vec

sealed trait Dataset[V <: Vec] {
  val name: String
  val indexFilePath: String
  val dims: Int
  val count: Int
}

object Dataset {
  case object Deep10M extends Dataset[Vec.DenseFloat] {
    val name = "deep-10M"
    val indexFilePath = "deep1b/base.1B.fbin.crop_nb_10000000"
    val dims = 96
    val count = 10000000
  }
  case object MSTuring1M extends Dataset[Vec.DenseFloat] {
    val name = "msturing-1M"
    val indexFilePath = "MSTuringANNS/base1b.fbin.crop_nb_1000000"
    val dims = 100
    val count = 1000000
  }
  val All: List[Dataset[_ <: Vec.KnownDims]] = List(Deep10M, MSTuring1M)
}
