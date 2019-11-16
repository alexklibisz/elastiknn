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

    it("has four nodes") {
      for {
        catNodesRes <- client.execute(catNodes())
      } yield {
        catNodesRes.isSuccess shouldBe true
        catNodesRes.result should have length 4
        catNodesRes.result.map(_.nodeRole).sorted shouldBe Seq("-", "dil", "dil", "m").sorted
      }
    }

  }

}
