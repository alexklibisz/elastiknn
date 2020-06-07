package com.klibisz.elastiknn.storage

import java.io._
import java.security.{AccessController, PrivilegedAction}

import com.esotericsoftware.kryo.io.{UnsafeInput, UnsafeOutput}
import com.klibisz.elastiknn.api.Vec
import org.nustaq.serialization.FSTConfiguration

import scala.util.Try

/**
  * Abstraction for different storage layouts for Vecs.
  * Decoupled from the api.Vec case classes to support optimizations like streaming vectors in a read-once fashion.
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

    def fromByteArray(barr: Array[Byte]): SparseBool = {
      val arr = FST.conf.asObject(barr).asInstanceOf[Array[Int]]
      new SparseBool {
        override val totalIndices: Int = arr.last
        override val trueIndicesLength: Int = arr.length - 1
        override def apply(i: Int): Int = arr(i)
      }
    }

    def encodeVec(vec: Vec.SparseBool): Array[Byte] = FST.conf.asByteArray(vec.trueIndices :+ vec.totalIndices)

//    def fromByteArray(barr: Array[Byte]): SparseBool =
//      AccessController.doPrivileged(new PrivilegedAction[SparseBool] {
//        override def run(): SparseBool = {
//          val arr = fst.asObject(barr).asInstanceOf[Array[Int]]
//          new SparseBool {
//            override val totalIndices: Int = arr.last
//            override val trueIndicesLength: Int = arr.length - 1
//            override def apply(i: Int): Int = arr(i)
//          }
//        }
//      })
//
//    def encodeVec(vec: Vec.SparseBool): Array[Byte] =
//      AccessController.doPrivileged(new PrivilegedAction[Array[Byte]] {
//        override def run(): Array[Byte] = {
//          fst.asByteArray(vec.trueIndices :+ vec.totalIndices)
//        }
//      })

//    def fromByteArray(barr: Array[Byte]): SparseBool =
//      AccessController.doPrivileged(new PrivilegedAction[SparseBool] {
//        override def run(): SparseBool = {
//          val kin = new UnsafeInput(barr)
//          val total = kin.readInt()
//          val trueLength = kin.readInt()
//          val trueIndices = kin.readInts(trueLength)
//          kin.close()
//          new SparseBool {
//            override def totalIndices: Int = total
//            override def trueIndicesLength: Int = trueLength
//            override def apply(i: Int): Int = trueIndices(i)
//          }
//        }
//      })
//
//    def encodeVec(vec: Vec.SparseBool): Array[Byte] =
//      AccessController.doPrivileged(new PrivilegedAction[Array[Byte]] {
//        override def run(): Array[Byte] = {
////          val unsafe = {
////            val f = classOf[sun.misc.Unsafe].getDeclaredField("theUnsafe")
////            f.setAccessible(true)
////            f.get(null).asInstanceOf[sun.misc.Unsafe]
////          }
////          unsafe.putInt()
//
//          val kout = new UnsafeOutput((vec.trueIndices.length + 2) * 4)
//          kout.writeInt(vec.totalIndices)
//          kout.writeInt(vec.trueIndices.length)
//          kout.writeInts(vec.trueIndices)
//          kout.close()
//          kout.toBytes
//        }
//      })

//    def fromByteArray(barr: Array[Byte]): SparseBool = {
//      val bin = new ByteArrayInputStream(barr)
//      val oin = new ObjectInputStream(bin)
//      val arr = oin.readObject().asInstanceOf[Array[Int]]
//      new SparseBool {
//        override def totalIndices: Int = arr.head
//        override def trueIndicesLength: Int = arr.length - 1
//        override def apply(i: Int): Int = arr(i + 1)
//      }
//    }
//
//    def encodeVec(vec: Vec.SparseBool): Array[Byte] = {
//      val bout = new ByteArrayOutputStream()
//      val oout = new ObjectOutputStream(bout)
//      oout.writeObject(vec.totalIndices +: vec.trueIndices)
//      bout.toByteArray
//    }
  }

  object DenseFloat {

    def fromByteArray(barr: Array[Byte]): DenseFloat = new DenseFloat {
      private val values = FST.conf.asObject(barr).asInstanceOf[Array[Float]]
      override def length: Int = values.length
      override def apply(i: Int): Float = values(i)
    }

    def encodeVec(vec: Vec.DenseFloat): Array[Byte] = FST.conf.asByteArray(vec.values)

//    def fromByteArray(barr: Array[Byte]): DenseFloat =
//      AccessController.doPrivileged(new PrivilegedAction[DenseFloat] {
//        override def run(): DenseFloat = {
//          val kout = new UnsafeInput(barr)
//          val len = kout.readInt()
//          val values = kout.readFloats(len)
//          new DenseFloat {
//            override def length: Int = len
//            override def apply(i: Int): Float = values(i)
//          }
//        }
//      })
//
//    def encodeVec(vec: Vec.DenseFloat): Array[Byte] =
//      AccessController.doPrivileged(new PrivilegedAction[Array[Byte]] {
//        override def run(): Array[Byte] = {
//          val kout = new UnsafeOutput((vec.values.length + 1) * 4)
//          kout.writeInt(vec.values.length)
//          kout.writeFloats(vec.values)
//          kout.close()
//          kout.toBytes
//        }
//      })

//    def fromByteArray(barr: Array[Byte]): DenseFloat = {
//      val bin = new ByteArrayInputStream(barr)
//      val oin = new ObjectInputStream(bin)
//      val arr = oin.readObject().asInstanceOf[Array[Float]]
//      new DenseFloat {
//        override def length: Int = arr.length
//        override def apply(i: Int): Float = arr(i)
//      }
//    }
//
//    def encodeVec(vec: Vec.DenseFloat): Array[Byte] = {
//      val bout = new ByteArrayOutputStream()
//      val oout = new ObjectOutputStream(bout)
//      oout.writeObject(vec.values)
//      bout.toByteArray
//    }
  }

}
