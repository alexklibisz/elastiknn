package com.klibisz.elastiknn.testing

import com.klibisz.elastiknn.client.ElastiknnClient
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.{ElasticClient, Executor}
import com.sksamuel.elastic4s.ElasticDsl._
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.scalatest.AsyncTestSuite

import scala.concurrent.Future

trait ElasticAsyncClient {

  this: AsyncTestSuite =>

  lazy val httpHost: HttpHost = new HttpHost("localhost", 9200)

  protected def deleteIfExists(index: String): Future[Unit] =
    for {
      ex <- eknn.execute(indexExists(index)).map(_.result.exists).recover { case _ => false }
      _ <- if (ex) eknn.execute(deleteIndex(index)) else Future.successful(())
    } yield ()

  protected lazy val eknn: ElastiknnClient[Future] = ElastiknnClient.futureClient(httpHost.getHostName, httpHost.getPort)

  protected lazy val client: ElasticClient = eknn.elasticClient

}
