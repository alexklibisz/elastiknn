package com.klibisz.elastiknn

import java.nio.{ByteBuffer, ByteOrder}

object Playground extends App {

  val arr = (0 until 10).toArray
  val bb: ByteBuffer = ByteBuffer.allocate(arr.length * 4).order(ByteOrder.nativeOrder())
  bb.asIntBuffer().put(arr)
  println(bb.array().length)

}
