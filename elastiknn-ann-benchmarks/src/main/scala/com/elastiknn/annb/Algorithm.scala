package com.elastiknn.annb

sealed abstract class Algorithm(val name: String)

object Algorithm {
  case object ElastiknnL2Lsh extends Algorithm("elastiknn-l2-lsh")
  val All = Seq(ElastiknnL2Lsh)
}
