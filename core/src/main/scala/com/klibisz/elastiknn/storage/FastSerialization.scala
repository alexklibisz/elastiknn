package com.klibisz.elastiknn.storage

import java.security.{AccessController, PrivilegedAction}

import org.nustaq.serialization.FSTConfiguration

import scala.util.Try

private[storage] object FastSerialization {

  private val intArrayClass = classOf[Array[Int]]
  private val floatArrayClass = classOf[Array[Float]]

  // Wrapped in a Try for initialization because that yields better error messages if it fails to initialize.
  private val conf: FSTConfiguration = Try {
    AccessController.doPrivileged(new PrivilegedAction[FSTConfiguration] {
      override def run(): FSTConfiguration = {
        val conf = FSTConfiguration.createUnsafeBinaryConfiguration()
        conf.setShareReferences(false)
        conf.registerClass(intArrayClass)
        conf.registerClass(floatArrayClass)
        conf
      }
    })
  }.get

  def writeInts(arr: Array[Int]): Array[Byte] = conf.asByteArray(arr)

//  def writeInts(arr: Array[Int]): Array[Byte] =
//    AccessController.doPrivileged(new PrivilegedAction[Array[Byte]] {
//      override def run(): Array[Byte] = {
//        val bout = new ByteArrayOutputStream((arr.length + 1) * 4)
//        val oout = new FSTObjectOutputNoShared(bout, conf)
//        oout.writeInt(arr.length)
//        arr.foreach(oout.writeInt)
//        oout.close()
//        bout.toByteArray
//      }
//    })

  def writeFloats(arr: Array[Float]): Array[Byte] = conf.asByteArray(arr)

  def readInts(barr: Array[Byte]): Array[Int] = conf.asObject(barr).asInstanceOf[Array[Int]]

//  def readInts(barr: Array[Byte]): Array[Int] = {
//    val oin = new FSTObjectInputNoShared(new ByteArrayInputStream(barr))
//    val length = oin.readInt()
//    val arr = new Array[Int](length)
//    arr.indices.foreach(i => arr.update(i, oin.readInt()))
//    oin.close()
//    arr
//  }

//  def readInts(barr: Array[Byte]): Array[Int] = {
//    val oin = new FSTObjectInputNoShared(new ByteArrayInputStream(barr), conf)
//    val arr = oin.readObject(intArrayClass).asInstanceOf[Array[Int]]
//    oin.close()
//    arr
//  }

//  def readInts(barr: Array[Byte]): Array[Int] = {
//    val oin = conf.getObjectInput(barr)
//    oin.readObject(classOf[Array[Int]]).asInstanceOf[Array[Int]]
//  }

  def readFloats(barr: Array[Byte]): Array[Float] = conf.asObject(barr).asInstanceOf[Array[Float]]

}
