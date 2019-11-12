package com.klibisz.elastiknn.testing

import org.scalatest.{AsyncFunSpec, Matchers, Succeeded}

class ContainerSupportSpec extends AsyncFunSpec with Matchers with ContainerSupport {

  describe("container setup") {

    it("starts an elasticsearch container with the plugin installed") {
      for {
        _ <- startContainer()
      } yield {
        Succeeded
      }
    }

    it("stops the container") {
      for {
        _ <- stopContainer()
      } yield {
        Succeeded
      }
    }

  }

}
