package com.klibisz.elastiknn.storage

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class UnsafeSerializationSpec extends AnyFreeSpec with Matchers {

  "writeInts and readInts" - {
    "round trip with randomized arrays" in {
      val seed = System.currentTimeMillis()
      val maxLen = 4096
      val rng = new Random(seed)
      for (i <- 0 to 1000) {
        withClue(s"Failed on iteration $i with seed $seed and max length $maxLen") {
          // Generate array of random ints.
          val len = rng.nextInt(maxLen)
          val iarr = (0 until len).map(_ => rng.nextInt(Int.MaxValue) * (if (rng.nextBoolean()) 1 else -1)).toArray

          // Serialize and check serialized length.
          val trimmed = UnsafeSerialization.writeInts(iarr)
          trimmed should have length (iarr.length * UnsafeSerialization.numBytesInInt)

          // Deserialize and check.
          val iarrReadTrimmed = UnsafeSerialization.readInts(trimmed, 0, trimmed.length)
          iarrReadTrimmed shouldBe iarr

          // Place in larger array with random offset.
          val offset = rng.nextInt(maxLen)
          val embedded = new Array[Byte](offset) ++ trimmed ++ new Array[Byte](rng.nextInt(maxLen))

          // Deserialize and check.
          val iarrReadEmbedded = UnsafeSerialization.readInts(embedded, offset, trimmed.length)
          iarrReadEmbedded shouldBe iarr
        }
      }
    }
  }

  "writeIntsWithPrefix" - {
    "equivalent to writeInts with prefix embedded in the array" in {
      val seed = System.currentTimeMillis()
      val maxLen = 4096
      val rng = new Random(seed)
      for (_ <- 0 to 1000) {
        val len = rng.nextInt(maxLen)
        val prefix = rng.nextInt()
        val iarr = (0 until len).map(_ => rng.nextInt(Int.MaxValue) * (if (rng.nextBoolean()) 1 else -1)).toArray
        val iarrWithPrefix = prefix +: iarr
        val writeIntsWithPrefix = UnsafeSerialization.writeIntsWithPrefix(prefix, iarr)
        val writeInts = UnsafeSerialization.writeInts(iarrWithPrefix)
        writeIntsWithPrefix shouldBe writeInts
      }
    }
  }

  "writeFloats and readFloats" - {
    "round trip with randomized arrays" in {
      val seed = System.currentTimeMillis()
      val maxLen = 4096
      val rng = new Random(seed)
      for (i <- 0 to 1000) {
        withClue(s"Failed on iteration $i with seed $seed and max length $maxLen") {
          // Generate array of random floats.
          val len = rng.nextInt(maxLen)
          val farr = (0 until len).map(_ => rng.nextFloat() * (if (rng.nextBoolean()) Float.MaxValue else Float.MinValue)).toArray

          // Serialize and check length.
          val trimmed = UnsafeSerialization.writeFloats(farr)
          trimmed should have length (farr.length * UnsafeSerialization.numBytesInFloat)

          // Deserialize and check.
          val farrTrimmed = UnsafeSerialization.readFloats(trimmed, 0, trimmed.length)
          farrTrimmed shouldBe farr

          // Place in larger array with random offset.
          val offset = rng.nextInt(maxLen)
          val embedded = new Array[Byte](offset) ++ trimmed ++ new Array[Byte](rng.nextInt(maxLen))

          // Deserialize and check.
          val farrReadEmbedded = UnsafeSerialization.readFloats(embedded, offset, trimmed.length)
          farrReadEmbedded shouldBe farr
        }
      }
    }
  }

  "writeInt" - {
    "variable length encoding" in {
      UnsafeSerialization.writeInt(127) should have length 1
      UnsafeSerialization.writeInt(-127) should have length 1
      UnsafeSerialization.writeInt(32767) should have length 2
      UnsafeSerialization.writeInt(-32767) should have length 2
    }
  }

  "writeInt and readInt" - {
    "round trip with randomized ints" in {
      val seed = System.currentTimeMillis()
      val rng = new Random(seed)
      for (i <- 0 to 10000) {
        withClue(s"Failed on iteration $i with seed $seed") {
          val i = rng.nextInt(Int.MaxValue) * (if (rng.nextBoolean()) 1 else -1)
          val barr = UnsafeSerialization.writeInt(i)
          val iRead = UnsafeSerialization.readInt(barr)
          iRead shouldBe i
        }
      }
    }
  }
}
