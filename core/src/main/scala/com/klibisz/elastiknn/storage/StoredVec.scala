package com.klibisz.elastiknn.storage

import com.klibisz.elastiknn.api.Vec

/**
  * Abstraction for different vector storage layouts and typeclasses for encoding/decoding them.
  * This is decoupled from the api Vec case classes so we can support various optimizations that might change the
  * interface, e.g. streaming the vectors in a read-once fashion. Currently the fastest storage methods support roughly
  * the same interface.
  *
  * The current default serialization method is using the FST library: https://github.com/RuedigerMoeller/fast-serialization
  * It's the fastest I was able to find, followed closely by Kryo's Unsafe serialization, then standard java
  * ObjectOutputStream/ObjectInputStream, then Protocol Buffers, and finally DataOutputStream/DataInputStream in a distant
  * last. Protocol Buffers produce the smallest byte arrays because they use variable-length encoding, but this doesn't
  * seem to help because Elasticsearch compresses the byte arrays anyways, and it actually hurts because reading them
  * requires some additional logic.
  *
  * FST and Kryo both come with the tradeoff that they require extra security permissions to run. If this becomes a problem,
  * it seems reasonable to switch to ObjectOutputStream/ObjectInputStream which is about 40% slower but doesn't require
  * these permissions.
  */
sealed trait StoredVec

object StoredVec {

  sealed trait SparseBool extends StoredVec {
    val trueIndices: Array[Int]
  }

  sealed trait DenseFloat extends StoredVec {
    val values: Array[Float]
  }

  object SparseBool {
    def encodeVec(vec: Vec.SparseBool): Array[Byte] = UnsafeSerialization.writeInts(vec.trueIndices)
    def fromByteArray(barr: Array[Byte]): SparseBool = new SparseBool {
      override val trueIndices: Array[Int] = UnsafeSerialization.readInts(barr)
    }
  }

  object DenseFloat {
    def encodeVec(vec: Vec.DenseFloat): Array[Byte] = FastSerialization.writeFloats(vec.values)
    def fromByteArray(barr: Array[Byte]): DenseFloat = new DenseFloat {
      override val values: Array[Float] = FastSerialization.readFloats(barr)
    }
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
