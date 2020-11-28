package com.klibisz.elastiknn

import com.klibisz.elastiknn.testing.{Elastic4sMatchers, ElasticAsyncClient}
import com.sksamuel.elastic4s.ElasticDsl._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

class ClusterSpec extends AsyncFunSuite with Matchers with Elastic4sMatchers with ElasticAsyncClient {

  test("returns green health") {
    for {
      healthRes <- client.execute(catHealth())
    } yield {
      healthRes.shouldBeSuccess
      healthRes.result.status shouldBe "green"
    }
  }

  test("installed the plugin") {
    for {
      catPluginsRes <- client.execute(catPlugins())
    } yield {
      catPluginsRes.shouldBeSuccess
      catPluginsRes.result should not be empty
      catPluginsRes.result.head.component shouldBe "elastiknn"
    }
  }

  test("started four nodes") {
    for {
      catNodesRes <- client.execute(catNodes())
    } yield {
      catNodesRes.shouldBeSuccess
      catNodesRes.result should have length 2
      catNodesRes.result.map(_.nodeRole).sorted shouldBe List("dilrt", "mr").sorted
    }
  }

}
