package com.klibisz.elastiknn

import com.sksamuel.elastic4s.ElasticDsl._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

class ClusterCatSuite extends AsyncFunSuite with Matchers with Elastic4sMatchers with ElasticAsyncClient {

  test("returns health") {
    for {
      healthRes <- client.execute(catHealth())
    } yield {
      healthRes.shouldBeSuccess
      // TODO: Figure out why the cluster is spuriously returning "yellow" in GH Actions, but not locally.
      // healthRes.result.status shouldBe "green"
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

  test("started two nodes") {
    for {
      catNodesRes <- client.execute(catNodes())
    } yield {
      catNodesRes.shouldBeSuccess
      catNodesRes.result should have length 2
      catNodesRes.result.map(_.nodeRole).sorted shouldBe List("cdfhilrstw", "mr")
    }
  }

}
