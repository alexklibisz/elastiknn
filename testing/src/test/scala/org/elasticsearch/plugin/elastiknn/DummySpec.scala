package org.elasticsearch.plugin.elastiknn

import org.scalatest.{FunSpec, Matchers}

class DummySpec extends FunSpec with Matchers {

  describe("Dumb stuff") {
    it("is dumb") {
      println(System.getProperty("project.dir"))
      Seq(1,2,3) should have length(3)
    }
  }

}