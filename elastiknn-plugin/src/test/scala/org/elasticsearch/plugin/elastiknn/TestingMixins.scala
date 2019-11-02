package org.elasticsearch.plugin.elastiknn

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.http.JavaClient
import org.elasticsearch.client.RestClient

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait TestingMixins {

  def defaultAwaitDuration: Duration = 10.seconds

  def elasticClient(rc: RestClient): ElasticClient =
    ElasticClient(JavaClient.fromRestClient(rc))

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
