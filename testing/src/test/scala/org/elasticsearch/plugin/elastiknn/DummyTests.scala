package org.elasticsearch.plugin.elastiknn

import org.scalatest.{FunSpec, Matchers}

class DummyTests extends FunSpec with Matchers {

  describe("Dumb stuff") {
    it("is dumb") {
      Seq(1,2,3) should have length(3)
    }
  }

}