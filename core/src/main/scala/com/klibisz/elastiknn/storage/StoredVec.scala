package com.klibisz.elastiknn.storage

import com.klibisz.elastiknn.api.Vec

/**
  * Optimized storage layout for Vecs.
  * Encoder converts a Vec to a byte array.
  * Decoder parses the byte array in a read-once fashion.
  */
sealed trait StoredVec

object StoredVec {

  trait Codec[V <: Vec, S <: StoredVec] {
    def apply(barr: Array[Byte]): S
    def apply(vec: V): Array[Byte]
  }

  object Codec {
    implicit def derived[V <: Vec: Encoder, S <: StoredVec: Decoder]: Codec[V, S] =
      new Codec[V, S] {
        override def apply(barr: Array[Byte]): S = implicitly[Decoder[S]].apply(barr)
        override def apply(vec: V): Array[Byte] = implicitly[Encoder[V]].apply(vec)
      }
  }

  trait Decoder[S <: StoredVec] {
    def apply(barr: Array[Byte]): S
  }

  object Decoder {
    implicit val sparseBool: Decoder[SparseBool] = (barr: Array[Byte]) => SparseBool.fromByteArray(barr)
    implicit val denseFloat: Decoder[DenseFloat] = (barr: Array[Byte]) => DenseFloat.fromByteArray(barr)
  }

  trait Encoder[V <: Vec] {
    def apply(vec: V): Array[Byte]
  }

  object Encoder {
    implicit val sparseBool: Encoder[Vec.SparseBool] = (vec: Vec.SparseBool) => SparseBool.encodeVec(vec)
    implicit val denseFloat: Encoder[Vec.DenseFloat] = (vec: Vec.DenseFloat) => DenseFloat.encodeVec(vec)
  }

  sealed trait SparseBool extends StoredVec {
    def totalIndices: Int
    def trueIndicesLength: Int
    def apply(i: Int): Int
  }

  sealed trait DenseFloat extends StoredVec {
    def length: Int
    def apply(i: Int): Int
  }

  object SparseBool {
    def fromByteArray(barr: Array[Byte]): SparseBool = ???
    def fromVec(vec: Vec.SparseBool): SparseBool = ???
    def encodeVec(vec: Vec.SparseBool): Array[Byte] = ???
  }

  object DenseFloat {
    def fromByteArray(barr: Array[Byte]): DenseFloat = ???
    def fromVec(vec: Vec.DenseFloat): DenseFloat = ???
    def encodeVec(vec: Vec.DenseFloat): Array[Byte] = ???
  }

}
