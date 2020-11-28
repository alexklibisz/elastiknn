package com.klibisz.elastiknn.storage

import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Random

class BitBufferSuite extends AnyFunSuite with Matchers {

  test("IntBuffer manual test") {
    val ib = new BitBuffer.IntBuffer
    ib.putOne() // +1 = 1
    ib.putZero() // +0 = 1
    ib.putOne() // +4 = 5
    ib.putOne() // +8 = 13
    ib.toByteArray shouldBe UnsafeSerialization.writeInt(13)
  }

  test("IntBuffer randomized test") {
    val rng = new Random(0)
    for (_ <- 0 until 100) {
      val len = rng.nextInt(32)
      val bits = (0 until len).map(_ => rng.nextInt(2))
      val prefix = UnsafeSerialization.writeInt(rng.nextInt(Int.MaxValue))
      val expected = bits.zipWithIndex
        .map {
          case (b, i) => b * math.pow(2, i)
        }
        .sum
        .toInt
      val bitBuf = new BitBuffer.IntBuffer(prefix)
      bits.foreach(b => if (b == 0) bitBuf.putZero() else bitBuf.putOne())
      bitBuf.toByteArray shouldBe prefix ++ UnsafeSerialization.writeInt(expected)
    }
  }

}
