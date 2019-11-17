package com.klibisz.elastiknn.rest

import com.klibisz.elastiknn.{Distance, ElasticAsyncClient}
import com.klibisz.elastiknn.elastic4s._
import org.scalatest.concurrent.AsyncTimeLimitedTests
import org.scalatest.time.Span
import org.scalatest.{AsyncFunSuite, Inspectors, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._

class SetupRestActionSuite extends AsyncFunSuite with AsyncTimeLimitedTests with Matchers with Inspectors with ElasticAsyncClient {

  override def timeLimit: Span = 10.seconds

  test("hits the setup endpoint") {
    for {
      setupRes <- client.execute(ElastiKnnSetupRequest())
    } yield {
      setupRes.isSuccess shouldBe true
    }
  }

  test("installs stored scripts") {
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
