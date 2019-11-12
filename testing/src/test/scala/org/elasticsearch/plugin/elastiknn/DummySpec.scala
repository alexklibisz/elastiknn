package org.elasticsearch.plugin.elastiknn

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSpec, Matchers}

class DummySpec extends FunSpec with Matchers {

  describe("Dumb stuff") {
    it("is dumb") {
      val cfg = ConfigFactory.load()
      println("TEST", Seq(
        cfg.getString("elastiknn.testing.esVersion"),
        cfg.getString("elastiknn.testing.pluginDir")
      ))
      Seq(1,2,3) should have length(3)
    }
  }

}