package com.klibisz.elastiknn.client

import com.klibisz.elastiknn.api._
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.bulk.{BulkResponse, BulkResponseItem}
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.indexes.PutMappingResponse
import com.sksamuel.elastic4s.requests.mappings.PutMappingRequest
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.higherKinds
import scala.util.Random

class ElastiknnClient[F[_]](val elasticClient: ElasticClient)(implicit executor: Executor[F], functor: Functor[F]) extends AutoCloseable {

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

  override def close(): Unit = elasticClient.close()

}

object ElastiknnClient {

  def futureClient(hostName: String = "localhost", port: Int = 9200, strict: Boolean = false)(
      implicit ec: ExecutionContext): ElastiknnClient[Future] = {
    val host = new HttpHost(hostName, port)
    val rc: RestClient = RestClient.builder(host).build()
    val jc: JavaClient = new JavaClient(rc)
    new ElastiknnClient(ElasticClient(jc))(Executor.FutureExecutor(ec), Functor.FutureFunctor(ec))
  }

  def checkResponse[U](res: Response[U]): Either[ElasticError, U] = {
    @tailrec
    def findError(bulkResponseItems: Seq[BulkResponseItem], acc: Option[ElasticError] = None): Option[ElasticError] =
      if (bulkResponseItems.isEmpty) acc
      else
        bulkResponseItems.head.error match {
          case Some(err) =>
            Some(
              ElasticError(err.`type`,
                           err.reason,
                           Some(err.index_uuid),
                           Some(err.index),
                           Some(err.shard.toString),
                           Seq.empty,
                           None,
                           None,
                           None,
                           Seq.empty))
          case None => findError(bulkResponseItems.tail, acc)
        }
    if (res.isError) Left(res.error)
    else
      res.result match {
        case bulkResponse: BulkResponse if bulkResponse.hasFailures =>
          findError(bulkResponse.items) match {
            case Some(err) => Left(err)
            case None      => Left(ElasticError.fromThrowable(new RuntimeException(s"Unknown bulk execution error in response $res")))
          }
        case other => Right(other)
      }
  }

}

object Foo {

  def main(args: Array[String]): Unit = {
    implicit val rng: Random = new Random(0)
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val client: ElastiknnClient[Future] = ElastiknnClient.futureClient(strict = true)
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

      res <- client.nearestNeighbors("blah", NearestNeighborsQuery.Exact("vec", queryVec, Similarity.Jaccard), 10)
      check = ElastiknnClient.checkResponse(res)
      _ = println(check)
    } yield ()
    try Await.result(pipeline, Duration("10s"))
    finally client.close()

  }
}
