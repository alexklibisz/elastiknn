package com.klibisz.elastiknn.storage

import com.google.protobuf.wrappers.Int32Value
import com.klibisz.elastiknn.api.Vec

import scala.util.Try

// Codec for writing/reading objects to/from byte arrays. Mostly used for storing values.
trait ByteArrayCodec[T] {
  def apply(t: T): Array[Byte]
  def apply(a: Array[Byte]): Try[T]
}

object ByteArrayCodec {

  def encode[T: ByteArrayCodec](t: T): Array[Byte] = implicitly[ByteArrayCodec[T]].apply(t)
  def decode[T: ByteArrayCodec](a: Array[Byte]): Try[T] = implicitly[ByteArrayCodec[T]].apply(a)

  implicit val sparseBoolVector: ByteArrayCodec[Vec.SparseBool] = new ByteArrayCodec[Vec.SparseBool] {
    override def apply(t: Vec.SparseBool): Array[Byte] = SparseBoolVector(t.trueIndices, t.totalIndices).toByteArray
    override def apply(a: Array[Byte]): Try[Vec.SparseBool] = Try {
      val stored = SparseBoolVector.parseFrom(a)
      Vec.SparseBool(stored.trueIndices, stored.totalIndices)
    }
  }

  implicit val denseFloatVector: ByteArrayCodec[Vec.DenseFloat] = new ByteArrayCodec[Vec.DenseFloat] {
    override def apply(t: Vec.DenseFloat): Array[Byte] = {
      val dfv = DenseFloatVector(t.values)
      val barr = dfv.toByteArray
      barr
    }
    override def apply(a: Array[Byte]): Try[Vec.DenseFloat] = Try {
      val stored = DenseFloatVector.parseFrom(a)
      Vec.DenseFloat(stored.values)
    }
  }

  implicit val int: ByteArrayCodec[Int] = new ByteArrayCodec[Int] {
    override def apply(t: Int): Array[Byte] = {
      // Another way to do it. Not sure which is faster, but using the protobufs is easier to decode.
      // val buf = java.nio.ByteBuffer.allocate(4)
      // buf.putInt(t)
      // buf.array()
      Int32Value(t).toByteArray
    }
    override def apply(a: Array[Byte]): Try[Int] = Try(Int32Value.parseFrom(a).value)
  }

}
