package com.klibisz.elastiknn.rest

import com.klibisz.elastiknn.client.ElastiKnnDsl._
import com.klibisz.elastiknn.{Elastic4sMatchers, ElasticAsyncClient, Similarity}
import org.scalatest.concurrent.AsyncTimeLimitedTests
import org.scalatest.time.Span
import org.scalatest.{AsyncFunSuite, Inspectors, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._

class SetupRestActionSuite
    extends AsyncFunSuite
    with AsyncTimeLimitedTests
    with Matchers
    with Inspectors
    with Elastic4sMatchers
    with ElasticAsyncClient {

  override def timeLimit: Span = 10.seconds

  test("hits the setup endpoint") {
    for {
      setupRes <- client.execute(ElastiKnnSetupRequest())
    } yield {
      setupRes.shouldBeSuccess
    }
  }

  test("installs stored scripts") {
    val distances: Seq[String] =
      Similarity.values.tail.map(_.name.toLowerCase.replace("similarity_", ""))

    for {
      setupRes <- client.execute(ElastiKnnSetupRequest())
      getScriptRequests = distances.map(d => client.execute(GetScriptRequest(s"elastiknn-exact-$d")))
      getScriptResults <- Future.sequence(getScriptRequests)
    } yield {
      setupRes.shouldBeSuccess
      forAll(getScriptResults)(_.shouldBeSuccess)
    }
  }

}
