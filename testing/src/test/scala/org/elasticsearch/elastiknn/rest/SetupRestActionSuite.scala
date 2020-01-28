package org.elasticsearch.elastiknn.rest

import org.elasticsearch.elastiknn.client.ElastiKnnDsl._
import org.elasticsearch.elastiknn.{Elastic4sMatchers, ElasticAsyncClient, Similarity}
import org.elasticsearch.elastiknn.Similarity
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

}
