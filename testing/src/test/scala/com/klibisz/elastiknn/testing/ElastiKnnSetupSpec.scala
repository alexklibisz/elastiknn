package com.klibisz.elastiknn.testing

import org.scalatest.{AsyncFunSpec, Matchers}

class ElastiKnnSetupSpec extends AsyncFunSpec with Matchers with ClusterSupport with Elastic4sSupport {

  describe("cluster setup") {

    it ("hits the setup endpoint") {
      for {
        setupRes <- client.execute(ElastiKnnSetupRequest())
      } yield {
        setupRes.isSuccess shouldBe true
      }
    }

  }

}
