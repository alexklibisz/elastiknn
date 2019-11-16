package com.klibisz.elastiknn.testing

import org.scalatest.{AsyncFunSpec, Matchers}
import com.sksamuel.elastic4s.ElasticDsl._

class ClusterSpec extends AsyncFunSpec with Matchers with  Elastic4sClientSupport {

  describe("testing cluster setup") {

    it("hits the _cat/health endpoint") {
      for {
        healthRes <- client.execute(catHealth())
      } yield {
        healthRes.isSuccess shouldBe true
        healthRes.result.status shouldBe "green"
      }
    }

  }

}
