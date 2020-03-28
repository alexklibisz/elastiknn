package com.klibisz.elastiknn.client

import com.klibisz.elastiknn.api.Mapping
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.indexes.PutMappingResponse
import com.sksamuel.elastic4s.requests.mappings.PutMappingRequest
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.higherKinds

trait ElastiknnClient[F[_]] extends AutoCloseable {

  import com.klibisz.elastiknn.api.ElasticsearchCodec._

  protected implicit val executor: Executor[F]
  protected implicit val functor: Functor[F]

  val elasticClient: ElasticClient

  def putMapping(indexName: String, field: String, fieldMapping: Mapping): F[Response[PutMappingResponse]] = {
    val mappingJsonString =
      s"""
        |{
        |  "properties": {
        |    "$field": ${encode(fieldMapping).spaces2}
        |  }
        |}
        |""".stripMargin
    val req: PutMappingRequest = ElasticDsl.putMapping(Indexes(indexName)).rawSource(mappingJsonString)
    elasticClient.execute(req)
  }

}

object ElastiknnClient {

  def futureClient(host: HttpHost)(implicit ec: ExecutionContext): ElastiknnClient[Future] = {
    val rc: RestClient = RestClient.builder(host).build()
    val jc: JavaClient = new JavaClient(rc)
    new ElastiknnClient[Future] {
      protected implicit val executor: Executor[Future] = Executor.FutureExecutor(ec)
      protected implicit val functor: Functor[Future] = Functor.FutureFunctor(ec)
      override val elasticClient: ElasticClient = ElasticClient(jc)
      override def close(): Unit = elasticClient.close()
    }
  }

  def futureClient(hostname: String = "localhost", port: Int = 9200)(implicit ec: ExecutionContext): ElastiknnClient[Future] =
    futureClient(new HttpHost(hostname, port))

}

object Foo {

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val client: ElastiknnClient[Future] = ElastiknnClient.futureClient()
    lazy val pipeline = for {
      res <- client.elasticClient.execute(createIndex("foo"))
      _ = println(res)
      res <- client.putMapping("foo", "vec", Mapping.SparseBool(10))
      _ = println(res)
    } yield ()
    try Await.result(pipeline, Duration("10s"))
    finally client.close()

  }
}
