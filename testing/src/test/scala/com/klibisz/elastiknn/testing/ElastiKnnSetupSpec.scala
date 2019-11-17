package com.klibisz.elastiknn.testing

import com.klibisz.elastiknn.Distance
import com.klibisz.elastiknn.elastic4s._
import com.sksamuel.elastic4s.Executor
import org.scalatest.concurrent.AsyncTimeLimitedTests
import org.scalatest.time.Span
import org.scalatest.{AsyncFunSpec, Inspectors, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._

class ElastiKnnSetupSpec extends AsyncFunSpec with AsyncTimeLimitedTests with Matchers with Inspectors with Elastic4sClientSupport {

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
      val distances: Seq[String] = Distance.values.tail.map(_.name.toLowerCase.replace("distance_", ""))

      for {
        setupRes <- client.execute(ElastiKnnSetupRequest())
        getScriptRequests = distances.map(d => client.execute(GetScriptRequest(s"elastiknn-exact-$d")))
        getScriptResults <- Future.sequence(getScriptRequests)
      } yield {
        setupRes.isSuccess shouldBe true
        forAll(getScriptResults)(_.isSuccess shouldBe true)
      }
    }

  }

}
