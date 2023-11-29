package com.klibisz.elastiknn.storage

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ByteBufferSerializationSpec extends AnyFreeSpec with Matchers {

  "writeInt" - {
    "variable length encoding" in {
      ByteBufferSerialization.writeInt(127) should have length 1
      ByteBufferSerialization.writeInt(-127) should have length 1
      ByteBufferSerialization.writeInt(32767) should have length 2
      ByteBufferSerialization.writeInt(-32767) should have length 2
      println(Int.MaxValue)
      println(Int.MinValue)
    }
  }

  "writeFloats and readFloats" - {
    "example" in {
      val original = (-10 until 10).map(_.toFloat).toArray
      val serialized = ByteBufferSerialization.writeFloats(original)
      val deserialized = ByteBufferSerialization.readFloats(serialized, 0, serialized.length)
      deserialized.toList shouldBe original.toList
    }
    "example with offset and length" in {
      val (dropLeftFloats, dropRightFloats) = (2, 5)
      val (dropLeftBytes, dropRightBytes) = (dropLeftFloats * 4, dropRightFloats * 4)
      val original = (-10 until 10).map(_.toFloat).toArray
      val serialized = ByteBufferSerialization.writeFloats(original)
      val deserialized = ByteBufferSerialization.readFloats(serialized, dropLeftBytes, serialized.length - dropLeftBytes - dropRightBytes)
      deserialized.toList shouldBe original.drop(dropLeftFloats).dropRight(dropRightFloats).toList
    }
    "compatibility with UnsafeSerialization" in {
      val original = (-10 until 10).map(_.toFloat).toArray
      val unsafeSerialized = UnsafeSerialization.writeFloats(original)
      val byteBufferSerialized = ByteBufferSerialization.writeFloats(original)
      val unsafeDeserializedFromByteBuffer = ByteBufferSerialization.readFloats(byteBufferSerialized, 0, byteBufferSerialized.length)
      val byteBufferDeserializedFromUnsafe = ByteBufferSerialization.readFloats(unsafeSerialized, 0, unsafeSerialized.length)
      byteBufferSerialized.toList shouldBe unsafeSerialized.toList
      unsafeDeserializedFromByteBuffer.toList shouldBe original.toList
      byteBufferDeserializedFromUnsafe.toList shouldBe original.toList
    }
  }
}
