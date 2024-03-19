package com.klibisz.elastiknn.api

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class IntArrayBufferSpec extends AnyFreeSpec with Matchers {

  "append" in {
    val buf = new IntArrayBuffer(4)
    (0 to 10).foreach(buf.append)
    buf.toArray.toList shouldBe (0 to 10).toList
  }
}
