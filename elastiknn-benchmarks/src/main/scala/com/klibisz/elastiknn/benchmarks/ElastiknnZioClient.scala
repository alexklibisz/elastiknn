package com.klibisz.elastiknn.benchmarks

import com.klibisz.elastiknn.client.ElastiknnClient
import com.sksamuel.elastic4s.{ElasticClient, Handler, Response}
import zio._

import scala.concurrent.Future

object ElastiknnZioClient {

  trait Service extends ElastiknnClient[Task] {
    override def execute[T, U](t: T)(implicit handler: Handler[T, U], manifest: Manifest[U]): Task[Response[U]]
  }

  def fromFutureClient(host: String, port: Int, strictFailure: Boolean, timeoutMillis: Int): Layer[Throwable, Has[Service]] =
    ZLayer.fromEffect(ZIO.fromFuture { implicit ec =>
      val client = ElastiknnClient.futureClient(host, port, strictFailure, timeoutMillis)
      val service = new Service {
        override def execute[T, U](t: T)(implicit handler: Handler[T, U], manifest: Manifest[U]): Task[Response[U]] =
          Task.fromFuture(_ => client.execute(t))
        override val elasticClient: ElasticClient = client.elasticClient
        override def close(): Unit = client.close()
      }
      Future.successful(service)
    })

}
