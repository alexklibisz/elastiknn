package com.klibisz.elastiknn.storage

import java.security.{AccessController, PrivilegedAction}

import org.nustaq.serialization.FSTConfiguration

import scala.util.Try

private[storage] object FastSerialization {

  // Wrapped in a Try for initialization because that yields better error messages if it fails to initialize.
  private val conf: FSTConfiguration = Try {
    AccessController.doPrivileged(new PrivilegedAction[FSTConfiguration] {
      override def run(): FSTConfiguration = {
        val conf = FSTConfiguration.createUnsafeBinaryConfiguration()
        conf.registerClass(classOf[Array[Int]])
        conf.registerClass(classOf[Array[Float]])
        conf
      }
    })
  }.get

  def writeInts(arr: Array[Int]): Array[Byte] = conf.asByteArray(arr)

  def writeFloats(arr: Array[Float]): Array[Byte] = conf.asByteArray(arr)

  def readInts(barr: Array[Byte]): Array[Int] = conf.asObject(barr).asInstanceOf[Array[Int]]

  def readFloats(barr: Array[Byte]): Array[Float] = conf.asObject(barr).asInstanceOf[Array[Float]]

}
