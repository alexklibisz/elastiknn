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
  val All: List[Dataset[_ <: Vec.KnownDims]] = List(Deep10M)
}
