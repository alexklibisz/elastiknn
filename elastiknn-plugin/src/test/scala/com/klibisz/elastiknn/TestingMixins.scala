package com.klibisz.elastiknn

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.http.JavaClient
import org.apache.logging.log4j.{LogManager, Logger}
import org.elasticsearch.client.RestClient

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.collection.JavaConverters._

trait TestingMixins {

  private val logger: Logger = LogManager.getLogger(getClass)

  def defaultAwaitDuration: Duration = 10.seconds

  def elasticClient(rc: RestClient): ElasticClient = {
    logger.info(
      s"client connected to hosts: ${rc.getNodes.asScala.map(_.getHost).mkString(",")}")
    ElasticClient(JavaClient.fromRestClient(rc))
  }

  final def await[T](f: => Future[T]): Unit = {
    Await.result(f, defaultAwaitDuration)
    ()
  }

  final def await[T](dur: Duration = defaultAwaitDuration)(
      f: => Future[T]): Unit = {
    Await.result(f, dur)
    ()
  }

}
