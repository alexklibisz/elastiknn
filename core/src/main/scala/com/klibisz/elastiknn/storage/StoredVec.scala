package com.klibisz.elastiknn.storage

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

import com.klibisz.elastiknn.api.Vec

/**
  * Optimized storage layout for Vecs.
  * Encoder converts a Vec to a byte array.
  * Decoder parses the byte array in a read-once fashion.
  */
sealed trait StoredVec

object StoredVec {

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

  sealed trait SparseBool extends StoredVec {
    def totalIndices: Int
    def trueIndicesLength: Int
    def apply(i: Int): Int
  }

  sealed trait DenseFloat extends StoredVec {
    def length: Int
    def apply(i: Int): Float
  }

  object SparseBool {
    // TODO: this can be further optimized by writing/reading bytes and shorts instead for values smaller than 128 and 32,768, respectively.
    // Would need some way to denote the switch from bytes to shorts to ints.
    // Perhaps writing the number of bytes, shorts, and ints at the front of the byte array.

    def fromByteArray(barr: Array[Byte]): SparseBool = {
      val bin = new ByteArrayInputStream(barr)
      val din = new DataInputStream(bin)
      val total = din.readInt()
      val trueLen = din.readInt()
      new SparseBool {
        private var pos: Int = -1
        private var head: Int = -1
        override def totalIndices: Int = total
        override def trueIndicesLength: Int = trueLen
        override def apply(i: Int): Int = {
          while (pos < i) {
            pos += 1
            head = din.readShort()
          }
          head
        }
//        override def apply(i: Int): Int = {
//          if (i < pos)
//            throw new IndexOutOfBoundsException(
//              s"Attepted to access index $i after accessing index $pos. You can only access indices in ascending order.")
//          else {
//            while (pos < i) {
//              pos += 1
//              head = din.readInt()
//            }
//            head
//          }
//        }
      }
    }
    def encodeVec(vec: Vec.SparseBool): Array[Byte] = {
      val bout = new ByteArrayOutputStream()
      val dout = new DataOutputStream(bout)
      dout.writeInt(vec.totalIndices)
      dout.writeInt(vec.trueIndices.length)
      vec.trueIndices.foreach(dout.writeShort)
      bout.toByteArray
    }
  }

  object DenseFloat {
    def fromByteArray(barr: Array[Byte]): DenseFloat = {
      val bin = new ByteArrayInputStream(barr)
      val din = new DataInputStream(bin)
      val len = din.readInt()
      new DenseFloat {
        private var pos: Int = -1
        private var head: Float = -1
        override def length: Int = len
        override def apply(i: Int): Float = {
          if (i < pos)
            throw new IndexOutOfBoundsException(
              s"Attepted to access index $i after accessing index $pos. You can only access indices in ascending order.")
          else {
            while (pos < i) {
              pos += 1
              head = din.readFloat()
            }
            head
          }
        }
      }
    }
    def encodeVec(vec: Vec.DenseFloat): Array[Byte] = {
      val bout = new ByteArrayOutputStream()
      val dout = new DataOutputStream(bout)
      dout.writeInt(vec.values.length)
      vec.values.foreach(dout.writeFloat)
      bout.toByteArray
    }
  }

}
