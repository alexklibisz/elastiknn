package com.klibisz.elastiknn.testing

import com.klibisz.elastiknn.elastic4s._
import org.scalatest.{AsyncFunSpec, Matchers}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class ElastiKnnSetupSpec extends AsyncFunSpec with Matchers with Elastic4sClientSupport {

  override implicit def executionContext: ExecutionContextExecutor = ExecutionContext.global

  describe("plugin setup") {

    it ("hits the setup endpoint") {
      for {
        setupRes <- client.execute(ElastiKnnSetupRequest())
      } yield {
        setupRes.isSuccess shouldBe true
      }
    }

    it("installs stored scripts") {
      for {
        setupRes <- client.execute(ElastiKnnSetupRequest())
        getScriptRes <- client.execute(GetScriptRequest("elastiknn-exact-angular"))
      } yield {
        setupRes.isSuccess shouldBe true
        getScriptRes.isSuccess shouldBe true
      }
    }

  }

}
