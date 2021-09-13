package com.elastiknn.annb

sealed abstract class Algorithm(val name: String, val distance: String)

object Algorithm {
  case object ElastiknnL2Lsh extends Algorithm("elastiknn-l2lsh", "euclidean")
  val All = Seq(ElastiknnL2Lsh)
}
