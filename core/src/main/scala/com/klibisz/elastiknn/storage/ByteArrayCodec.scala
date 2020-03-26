package com.klibisz.elastiknn.storage

import com.klibisz.elastiknn.api.Vec

import scala.util.Try

trait ByteArrayCodec[T] {
  def apply(t: T): Array[Byte]
  def apply(a: Array[Byte]): Try[T]
}

object ByteArrayCodec {

  implicit val sparseBoolVector: ByteArrayCodec[Vec.SparseBool] = new ByteArrayCodec[Vec.SparseBool] {
    override def apply(t: Vec.SparseBool): Array[Byte] = SparseBoolVector(t.trueIndices, t.totalIndices).toByteArray
    override def apply(a: Array[Byte]): Try[Vec.SparseBool] = Try {
      val stored = SparseBoolVector.parseFrom(a)
      Vec.SparseBool(stored.trueIndices, stored.totalIndices)
    }
  }

  implicit val denseFloatVector: ByteArrayCodec[Vec.DenseFloat] = new ByteArrayCodec[Vec.DenseFloat] {
    override def apply(t: Vec.DenseFloat): Array[Byte] = DenseFloatVector(t.values).toByteArray
    override def apply(a: Array[Byte]): Try[Vec.DenseFloat] = Try {
      val stored = DenseFloatVector.parseFrom(a)
      Vec.DenseFloat(stored.values)
    }
  }

}
