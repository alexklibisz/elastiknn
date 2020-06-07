package com.klibisz.elastiknn.storage

import com.klibisz.elastiknn.api.Vec

/**
  * Abstraction for different storage layouts for Vecs.
  * Decoupled from the api.Vec case classes to support optimizations like streaming vectors in a read-once fashion.
  */
sealed trait StoredVec

object StoredVec {

  sealed trait SparseBool extends StoredVec {
    def trueIndices: Array[Int]
  }

  sealed trait DenseFloat extends StoredVec {
    def values: Array[Float]
  }

  object SparseBool {
    def fromByteArray(barr: Array[Byte]): SparseBool = new SparseBool {
      override val trueIndices: Array[Int] = FastSerialization.readInts(barr)
    }
    def encodeVec(vec: Vec.SparseBool): Array[Byte] = FastSerialization.writeInts(vec.trueIndices)
  }

  object DenseFloat {
    def fromByteArray(barr: Array[Byte]): DenseFloat = new DenseFloat {
      override val values: Array[Float] = FastSerialization.readFloats(barr)
    }
    def encodeVec(vec: Vec.DenseFloat): Array[Byte] = FastSerialization.writeFloats(vec.values)
  }

  /**
    * Typeclasses for converting api vecs to stored vecs.
    */
  trait Codec[V <: Vec, S <: StoredVec] {
    def decode(barr: Array[Byte]): S
    def encode(vec: V): Array[Byte]
  }

  object Codec {
    implicit def derived[V <: Vec: Encoder, S <: StoredVec: Decoder]: Codec[V, S] =
      new Codec[V, S] {
        override def decode(barr: Array[Byte]): S = implicitly[Decoder[S]].apply(barr)
        override def encode(vec: V): Array[Byte] = implicitly[Encoder[V]].apply(vec)
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

}
