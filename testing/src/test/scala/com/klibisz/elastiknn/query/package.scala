package com.klibisz.elastiknn

import io.circe._
import io.circe.generic.semiauto._
import scalapb._
import scalapb_circe.JsonFormat

import scala.util.{Failure, Success, Try}

package object query {

  implicit def decodeScalaPB[SPB <: GeneratedMessage with Message[SPB]](implicit ev: GeneratedMessageCompanion[SPB]): Decoder[SPB] =
    (c: HCursor) =>
      Try(JsonFormat.fromJson(c.value)) match {
        case Failure(ex) =>
          Left(DecodingFailure(ex.getLocalizedMessage, Nil))
        case Success(msg) => Right(msg)
    }

  case class Query(vector: ElastiKnnVector, similarities: Vector[Float], indices: Vector[Int])
  object Query {
    implicit def decQuery: Decoder[Query] = deriveDecoder[Query]
    implicit val ekvLike: ElastiKnnVectorLike[Query] = (a: Query) => a.vector
  }

  case class TestData(corpus: Vector[ElastiKnnVector], queries: Vector[Query])
  object TestData {
    implicit def decTestData: Decoder[TestData] = deriveDecoder[TestData]
  }

}
