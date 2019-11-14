package com.klibisz.elastiknn.testing

import java.io.File

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.http.JavaClient
import org.apache.http.HttpHost
import org.elasticsearch.client.{RestClient, RestClientBuilder}

import scala.concurrent.{ExecutionContext, Future}
import sys.process._


trait ClusterSupport {

  protected val testingDir = new File(System.getProperty("project.testingDir"))

  protected lazy val client: ElasticClient = {
    val rc = RestClient.builder(HttpHost.create("http://localhost:9200")).build()
    val jc = new JavaClient(rc)
    ElasticClient(jc)
  }

  def startContainer()(implicit ec: ExecutionContext): Future[Unit] =
    Future(Process("./cluster-start.sh", testingDir).!!)

  def stopContainer()(implicit ec: ExecutionContext): Future[Unit] =
    Future(Process("./cluster-stop.sh", testingDir).!!)

}