package com.klibisz.elastiknn.testing

import org.scalatest.{AsyncFunSpec, Matchers, Succeeded}
import org.testcontainers.images.builder.ImageFromDockerfile

import scala.concurrent.Future

class ContainerSupportSpec extends AsyncFunSpec with Matchers with ContainerSupport {

  describe("container setup") {

    it("starts an elasticsearch container which accepts a ping request") {
//      new ImageFromDockerfile()
      Future.successful(Succeeded)
    }

  }

}
