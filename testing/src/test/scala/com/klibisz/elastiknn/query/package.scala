package com.klibisz.elastiknn

import io.circe.Decoder
import io.circe.generic.semiauto._

package object query {

//  sealed trait TypedVector[T] {
//    def values: Array[T]
//  }
//  case class BooleanVector(values: Array[Boolean]) extends TypedVector[Boolean]
//  case class DoubleVector(values: Array[Double]) extends TypedVector[Double]

  case class Query[T : Decoder](vector: Array[T], distances: Seq[Float], indices: Seq[Int])
  object Query {
    implicit def decDouble: Decoder[Query[Double]] = deriveDecoder[Query[Double]]
    implicit def decBoolean: Decoder[Query[Boolean]] = deriveDecoder[Query[Boolean]]
  }

  case class TestData[T : Decoder](corpus: Seq[Array[T]], queries: Seq[Query[T]])
  object TestData {
    implicit def decDouble: Decoder[TestData[Double]] = deriveDecoder[TestData[Double]]
    implicit def decBoolean: Decoder[TestData[Boolean]] = deriveDecoder[TestData[Boolean]]
  }

}
