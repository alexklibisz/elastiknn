package com.klibisz.elastiknn.testing

import com.sksamuel.elastic4s.ElasticDsl._
import org.scalatest.{AsyncFunSpec, Matchers}

class ClusterSupportSpec extends AsyncFunSpec with Matchers with ClusterSupport {

  describe("container setup") {

    it("starts an elasticsearch container with the plugin installed") {
      for {
        _ <- startCluster()
        healthRes <- client.execute(catHealth())
      } yield {
        healthRes.isSuccess shouldBe true
        healthRes.result.status shouldBe "green"
      }
    }

    it("installs the elastiknn plugin") {
      for {
        _ <- startCluster()
        pluginsRes <- client.execute(catPlugins())
      } yield {
        pluginsRes.isSuccess shouldBe true
        pluginsRes.result should have length 1
        pluginsRes.result.head.component shouldBe "elastiknn"
      }
    }

    it("stops the container") {
      for {
        _ <- stopCluster()
        healthRes <- recoverToExceptionIf[RuntimeException] {
          client.execute(catHealth()).map(_.result)
        }
      } yield {
        healthRes.getMessage shouldBe "java.net.ConnectException: Connection refused"
      }
    }

  }

}
