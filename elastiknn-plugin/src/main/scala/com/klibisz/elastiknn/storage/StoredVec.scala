package com.klibisz.elastiknn.storage

import java.util

import com.klibisz.elastiknn.api.Vec

/** Abstraction for different vector storage layouts and typeclasses for encoding/decoding them. This is decoupled from the api Vec case
  * classes so we can support various optimizations that might change the interface, e.g. streaming the vectors in a read-once fashion.
  * Currently the fastest storage methods support roughly the same interface.
  *
  * The current default serialization method is using sun.misc.Unsafe to eek out the best possible performance. The implementation is based
  * mostly on the Kryo library's use of sun.misc.Unsafe. Many other options were considered:
  *   - fast-serialization library with unsafe configuration - roughly same as using Unsafe.
  *   - kryo library with unsafe input/output - a bit slower than fast-serialization and bare Unsafe.
  *   - java.io.ObjectOutput/InputStreams - 30-40% slower than Unsafe, but by far the best vanilla JVM solution.
  *   - protocol buffers - roughly same as ObjectOutput/InputStreams, but with smaller byte array sizes; the size doesn't seem to matter as
  *     it's compressed by ES anyway.
  *   - java.io.DataOutput/InputStreams - far slower.
  *   - scodec - far slower.
  *
  * Anything using Unsafe comes with the tradeoff that it requires extra JVM security permissions. If this becomes a problem we should
  * likely switch to ObjectOutput/InputStreams.
  */
sealed trait StoredVec

object StoredVec {

  sealed trait SparseBool extends StoredVec {
    val trueIndices: Array[Int]
    override def hashCode: Int = util.Arrays.hashCode(trueIndices)
  }

  object SparseBool {
    implicit val fromApiVec: Conversion[Vec.SparseBool, StoredVec.SparseBool] = (v: Vec.SparseBool) =>
      new SparseBool {
        override val trueIndices: Array[Int] = v.trueIndices
      }
  }

  sealed trait DenseFloat extends StoredVec {
    val values: Array[Float]
    override def hashCode: Int = util.Arrays.hashCode(values)
  }

  object DenseFloat {
    implicit val fromApiVec: Conversion[Vec.DenseFloat, StoredVec.DenseFloat] = (v: Vec.DenseFloat) =>
      new DenseFloat:
        override val values: Array[Float] = v.values
  }

  /** Typeclasses for converting api vecs to stored vecs.
    */
  trait Codec[V <: Vec, S <: StoredVec] {
    def decode(barr: Array[Byte], offset: Int, length: Int): S
    def encode(vec: V): Array[Byte]
  }

  object Codec {
    implicit def derived[V <: Vec: Encoder, S <: StoredVec: Decoder]: Codec[V, S] =
      new Codec[V, S] {
        override def decode(barr: Array[Byte], offset: Int, length: Int): S = summon[Decoder[S]].apply(barr, offset, length)
        override def encode(vec: V): Array[Byte] = summon[Encoder[V]].apply(vec)
      }
  }

  trait Decoder[S <: StoredVec] {
    def apply(barr: Array[Byte], offset: Int, length: Int): S
  }

  object Decoder {
    implicit val sparseBool: Decoder[SparseBool] = (barr: Array[Byte], offset: Int, length: Int) =>
      new SparseBool {
        override val trueIndices: Array[Int] = ByteBufferSerialization.readInts(barr, offset, length)
      }
    implicit val denseFloat: Decoder[DenseFloat] = (barr: Array[Byte], offset: Int, length: Int) =>
      new DenseFloat {
        override val values: Array[Float] = ByteBufferSerialization.readFloats(barr, offset, length)
      }
    implicit def derived[V <: Vec, S <: StoredVec](using codec: Codec[V, S]): Decoder[S] =
      (barr: Array[Byte], offset: Int, length: Int) => codec.decode(barr, offset, length)
  }

  trait Encoder[V <: Vec] {
    def apply(vec: V): Array[Byte]
  }

  object Encoder {
    implicit val sparseBool: Encoder[Vec.SparseBool] = (vec: Vec.SparseBool) => ByteBufferSerialization.writeInts(vec.trueIndices)
    implicit val denseFloat: Encoder[Vec.DenseFloat] = (vec: Vec.DenseFloat) => ByteBufferSerialization.writeFloats(vec.values)
  }

}
