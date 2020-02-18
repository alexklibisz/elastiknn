package com.klibisz.elastiknn

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

// Need this import to derive decoder for PB case classes.
import com.klibisz.elastiknn.utils.GeneratedMessageUtils._

case class TestData(corpus: Vector[ElastiKnnVector], queries: Vector[Query])
object TestData {
  implicit def decTestData: Decoder[TestData] = deriveDecoder[TestData]
}

case class Query(vector: ElastiKnnVector, similarities: Vector[Float], indices: Vector[Int])
object Query {
  implicit def decQuery: Decoder[Query] = deriveDecoder[Query]
  implicit val ekvLike: ElastiKnnVectorLike[Query] = (a: Query) => a.vector
}
