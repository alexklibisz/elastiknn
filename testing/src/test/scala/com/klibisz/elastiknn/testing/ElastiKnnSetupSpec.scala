package com.klibisz.elastiknn.testing

import com.klibisz.elastiknn.elastic4s._
import com.sksamuel.elastic4s.Executor
import org.scalatest.concurrent.AsyncTimeLimitedTests
import org.scalatest.time.Span
import org.scalatest.{AsyncFunSpec, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._

class ElastiKnnSetupSpec extends AsyncFunSpec with AsyncTimeLimitedTests with Matchers with Elastic4sClientSupport {

  override def timeLimit: Span = 10.seconds

  implicit def futureExecutor: Executor[Future] = Executor.FutureExecutor(this.executionContext)

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
