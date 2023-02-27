package com.klibisz.elastiknn

import com.klibisz.elastiknn.client.ElastiknnClient
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import org.apache.http.HttpHost
import org.scalatest.AsyncTestSuite

import scala.concurrent.Future
import scala.util.Success

trait ElasticAsyncClient {

  this: AsyncTestSuite =>

  lazy val httpHost: HttpHost = new HttpHost("localhost", 9200)

  protected def deleteIfExists(index: String): Future[Unit] =
    eknn.execute(deleteIndex(index)).transform {
      case _ => Success(())
    }

  protected lazy val eknn: ElastiknnClient[Future] = ElastiknnClient.futureClient(httpHost.getHostName, httpHost.getPort)

  protected lazy val client: ElasticClient = eknn.elasticClient

}
