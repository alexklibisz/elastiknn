package com.klibisz.elastiknn.storage

/**
  * Minimal abstraction to simplify storing a series of bits as a single scalar value, convertible to a byte array.
  */
sealed trait BitBuffer {
  def putOne(): Unit
  def putZero(): Unit
  def toByteArray: Array[Byte]
}

object BitBuffer {

  class IntBuffer(barr: Array[Byte] = Array.empty) extends BitBuffer {
    private var buf = 0
    private var i = 0
    override def putOne(): Unit = {
      buf += IntBuffer.powers(i)
      i += 1
    }
    override def putZero(): Unit = i += 1
    override def toByteArray: Array[Byte] = barr ++ UnsafeSerialization.writeInt(buf)
  }

  object IntBuffer {
    private val powers = (0 until 32).map(1 << _).toArray
  }

}
