package com.klibisz.elastiknn.client

import com.klibisz.elastiknn.KNearestNeighborsQuery
import com.klibisz.elastiknn.api.{ElasticsearchCodec, Mapping, NearestNeighborsQuery, Similarity, Vec}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.bulk.BulkResponse
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.indexes.{IndexRequest, PutMappingResponse}
import com.sksamuel.elastic4s.requests.mappings.PutMappingRequest
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.higherKinds
import scala.util.Random

trait ElastiknnClient[F[_]] extends AutoCloseable {

  protected implicit val executor: Executor[F]
  protected implicit val functor: Functor[F]

  val elasticClient: ElasticClient

  def putMapping(indexName: String, fieldName: String, fieldMapping: Mapping): F[Response[PutMappingResponse]] = {
    val mappingJsonString =
      s"""
        |{
        |  "properties": {
        |    "$fieldName": ${ElasticsearchCodec.encode(fieldMapping).spaces2}
        |  }
        |}
        |""".stripMargin
    val req: PutMappingRequest = ElasticDsl.putMapping(Indexes(indexName)).rawSource(mappingJsonString)
    elasticClient.execute(req)
  }

  def index(indexName: String,
            fieldName: String,
            vecs: Seq[Vec],
            ids: Option[Seq[String]] = None,
            refresh: RefreshPolicy = RefreshPolicy.NONE): F[Response[BulkResponse]] = {
    val reqs = vecs.map(v => ElastiknnRequests.indexVector(indexName, fieldName, v))
    val withIds = ids match {
      case Some(idSeq) if idSeq.length == reqs.length =>
        reqs.zip(idSeq).map {
          case (req, id) => req.id(id)
        }
      case _ => reqs
    }
    elasticClient.execute(bulk(withIds).refresh(refresh))
  }

  def nearestNeighbors(indexName: String,
                       query: NearestNeighborsQuery,
                       k: Int,
                       fetchSource: Boolean = true): F[Response[SearchResponse]] = {
    val req = search(indexName).query(ElastiknnRequests.nearestNeighborsQuery(query)).fetchSource(fetchSource).size(k)
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
    implicit val rng: Random = new Random(0)
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val client: ElastiknnClient[Future] = ElastiknnClient.futureClient()
    lazy val pipeline = for {
      _ <- client.elasticClient.execute(deleteIndex("foo"))
      res <- client.elasticClient.execute(createIndex("foo"))
      _ = println(res)
      res <- client.putMapping("foo", "vec", Mapping.SparseBool(10))
      _ = println(res)
      res <- client.index("foo", "vec", Vec.SparseBool.randoms(10, 100), refresh = RefreshPolicy.IMMEDIATE)
      _ = println(res)
      queryVec = Vec.SparseBool.random(10)
      res <- client.nearestNeighbors("foo", NearestNeighborsQuery.Exact("vec", queryVec, Similarity.Jaccard), 10)
      _ = println((queryVec, res))
    } yield ()
    try Await.result(pipeline, Duration("10s"))
    finally client.close()

  }
}
