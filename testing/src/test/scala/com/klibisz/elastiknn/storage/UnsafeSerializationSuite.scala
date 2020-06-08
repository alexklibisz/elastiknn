package com.klibisz.elastiknn.storage

import org.scalatest.{FunSuite, Matchers}

import scala.util.Random

class UnsafeSerializationSuite extends FunSuite with Matchers {

  test("arrays of ints") {
    val seed = System.currentTimeMillis()
    val maxLen = 4096
    val rng = new Random(seed)
    for (i <- 0 to 1000) {
      withClue(s"Failed on iteration $i with seed $seed and max length $maxLen") {
        val len = rng.nextInt(maxLen)
        val iarr = (0 until len).map(_ => rng.nextInt(Int.MaxValue) * (if (rng.nextBoolean()) 1 else -1)).toArray
        val barr = UnsafeSerialization.writeInts(iarr)
        val iarrRead = UnsafeSerialization.readInts(barr)
        barr should have length ((iarr.length + 1) * UnsafeSerialization.numBytesInInt)
        iarrRead shouldBe iarr
      }
    }
  }

  test("arrays of floats") {
    val seed = System.currentTimeMillis()
    val maxLen = 4096
    val rng = new Random(seed)
    for (i <- 0 to 1000) {
      withClue(s"Failed on iteration $i with seed $seed and max length $maxLen") {
        val len = rng.nextInt(maxLen)
        val farr = (0 until len).map(_ => rng.nextFloat() * (if (rng.nextBoolean()) Float.MaxValue else Float.MinValue)).toArray
        val barr = UnsafeSerialization.writeFloats(farr)
        val farrRead = UnsafeSerialization.readFloats(barr)
        barr should have length (farr.length * UnsafeSerialization.numBytesInFloat) + UnsafeSerialization.numBytesInInt
        farrRead shouldBe farr
      }
    }
  }
}
