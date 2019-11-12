package com.klibisz.elastiknn.testing

import java.io.File

import scala.concurrent.{ExecutionContext, Future}
import sys.process._


trait ContainerSupport {

  private val testingDir = new File(System.getProperty("project.testingDir"))

  def startContainer()(implicit ec: ExecutionContext): Future[Unit] = Future {
    Process("docker-compose up -d", testingDir).!!
    Process("curl localhost:9200/_cluster/health?wait_for_status=yellow&timeout=1s", testingDir).!!
  }


  def stopContainer()(implicit ec: ExecutionContext): Future[Unit] = Future {
    Process("docker-compose down", testingDir).!!
  }

}