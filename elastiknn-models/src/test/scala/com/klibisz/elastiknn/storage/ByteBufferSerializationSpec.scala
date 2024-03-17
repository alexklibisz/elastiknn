package com.klibisz.elastiknn.storage

import org.scalatest.Assertion
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class ByteBufferSerializationSpec extends AnyFreeSpec with Matchers {

  "writeInt and readInt" - {
    "round trip" - {
      def check(i: Int, expectedLengthOpt: Option[Int]): Assertion = {
        val serialized = ByteBufferSerialization.writeInt(i)
        val deserialized = ByteBufferSerialization.readInt(serialized)
        expectedLengthOpt.foreach(serialized.length shouldBe _)
        deserialized shouldBe i
      }
      "absolute value < Byte.MinValue" in {
        check(Byte.MinValue + 1, Some(1))
        check(Byte.MaxValue - 1, Some(1))
      }
      "absolute value < Short.MaxValue" in {
        check(Short.MinValue + 1, Some(2))
        check(Short.MaxValue - 1, Some(2))
      }
      "absolute value < Int.MaxValue" in {
        check(Int.MinValue + 1, Some(4))
        check(Int.MaxValue - 1, Some(4))
      }
      "randomized" in {
        val seed = System.currentTimeMillis()
        info(s"Using seed $seed")
        val rng = new Random(seed)
        (0 until 1000).foreach { _ =>
          val i = rng.nextInt()
          check(i, None)
        }
      }
    }
  }

  "writeInts and readInts" - {
    "round trip" - {
      "without offset and length" in {
        val original = (-10 until 10).toArray
        val serialized = ByteBufferSerialization.writeInts(original)
        val deserialized = ByteBufferSerialization.readInts(serialized, 0, serialized.length)
        deserialized.toList shouldBe original.toList
      }
      "with offset and length" in {
        val (dropLeftFloats, dropRightFloats) = (2, 5)
        val (dropLeftBytes, dropRightBytes) = (dropLeftFloats * 4, dropRightFloats * 4)
        val original = (-10 until 10).toArray
        val serialized = ByteBufferSerialization.writeInts(original)
        val deserialized = ByteBufferSerialization.readInts(serialized, dropLeftBytes, serialized.length - dropLeftBytes - dropRightBytes)
        deserialized.toList shouldBe original.drop(dropLeftFloats).dropRight(dropRightFloats).toList
      }
    }
  }

  "writeIntsWithPrefix and readInts" - {
    "round trip" - {
      "without offset and length" in {
        val original = (-10 until 10).toArray
        val serialized = ByteBufferSerialization.writeIntsWithPrefix(original.head, original.tail)
        val deserialized = ByteBufferSerialization.readInts(serialized, 0, serialized.length)
        deserialized.toList shouldBe original.toList
      }
      "with offset and length" in {
        val (dropLeftFloats, dropRightFloats) = (2, 5)
        val (dropLeftBytes, dropRightBytes) = (dropLeftFloats * 4, dropRightFloats * 4)
        val original = (-10 until 10).toArray
        val serialized = ByteBufferSerialization.writeIntsWithPrefix(original.head, original.tail)
        val deserialized = ByteBufferSerialization.readInts(serialized, dropLeftBytes, serialized.length - dropLeftBytes - dropRightBytes)
        deserialized.toList shouldBe original.drop(dropLeftFloats).dropRight(dropRightFloats).toList
      }
    }
  }

  "writeFloats and readFloats" - {
    "round trip" - {
      "without offset and length" in {
        val original = (-10 until 10).map(_.toFloat).toArray
        val serialized = ByteBufferSerialization.writeFloats(original)
        val deserialized = ByteBufferSerialization.readFloats(serialized, 0, serialized.length)
        deserialized.toList shouldBe original.toList
      }
      "with offset and length" in {
        val (dropLeftFloats, dropRightFloats) = (2, 5)
        val (dropLeftBytes, dropRightBytes) = (dropLeftFloats * 4, dropRightFloats * 4)
        val original = (-10 until 10).map(_.toFloat).toArray
        val serialized = ByteBufferSerialization.writeFloats(original)
        val deserialized = ByteBufferSerialization.readFloats(serialized, dropLeftBytes, serialized.length - dropLeftBytes - dropRightBytes)
        deserialized.toList shouldBe original.drop(dropLeftFloats).dropRight(dropRightFloats).toList
      }
    }
  }
}
