package com.klibisz.elastiknn.client

import com.klibisz.elastiknn.api._
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.bulk.{BulkResponse, BulkResponseItem}
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.indexes.PutMappingResponse
import com.sksamuel.elastic4s.requests.mappings.PutMappingRequest
import com.sksamuel.elastic4s.requests.searches.{SearchRequest, SearchResponse}
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

trait ElastiknnClient[F[_]] extends AutoCloseable {

  val elasticClient: ElasticClient

  def execute[T, U](t: T)(implicit handler: Handler[T, U], manifest: Manifest[U]): F[Response[U]]

  def putMapping(index: String, field: String, mapping: Mapping): F[Response[PutMappingResponse]] =
    execute(ElastiknnRequests.putMapping(index, field, mapping))

  def index(index: String,
            field: String,
            vecs: Seq[Vec],
            ids: Option[Seq[String]] = None,
            refresh: RefreshPolicy = RefreshPolicy.NONE): F[Response[BulkResponse]] = {
    val reqs = vecs.map(v => ElastiknnRequests.indexVec(index, field, v))
    val withIds = ids match {
      case Some(idSeq) if idSeq.length == reqs.length =>
        reqs.zip(idSeq).map {
          case (req, id) => req.id(id)
        }
      case _ => reqs
    }
    execute(bulk(withIds).refresh(refresh))
  }

  def nearestNeighbors(index: String,
                       query: NearestNeighborsQuery,
                       k: Int,
                       fetchSource: Boolean = false,
                       preference: Option[String] = None): F[Response[SearchResponse]] =
    execute(ElastiknnRequests.nearestNeighborsQuery(index, query, k, fetchSource, preference))

}

object ElastiknnClient {

  final case class StrictFailureException(message: String, cause: Throwable = None.orNull) extends RuntimeException(message, cause)

  def futureClient(host: String = "localhost", port: Int = 9200, strictFailure: Boolean = true)(
      implicit ec: ExecutionContext): ElastiknnFutureClient = {
    val rc: RestClient = RestClient.builder(new HttpHost(host, port)).build()
    val jc: JavaClient = new JavaClient(rc)
    new ElastiknnFutureClient {
      implicit val executor: Executor[Future] = Executor.FutureExecutor(ec)
      implicit val functor: Functor[Future] = Functor.FutureFunctor(ec)
      val elasticClient: ElasticClient = ElasticClient(jc)
      override def execute[T, U](req: T)(implicit handler: Handler[T, U], manifest: Manifest[U]): Future[Response[U]] = {
        val future: Future[Response[U]] = elasticClient.execute(req)
        if (strictFailure) future.flatMap { res =>
          checkResponse(req, res) match {
            case Left(ex) => Future.failed(ex)
            case Right(_) => Future.successful(res)
          }
        } else future
      }

      override def toString: String = s"${ElastiknnClient.getClass.getSimpleName} connected to $host:$port"
      override def close(): Unit = elasticClient.close()
    }
  }

  private def checkResponse[T, U](req: T, res: Response[U]): Either[Throwable, U] = {
    @tailrec
    def findBulkError(bulkResponseItems: Seq[BulkResponseItem], acc: Option[ElasticError] = None): Option[ElasticError] =
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
          case None => findBulkError(bulkResponseItems.tail, acc)
        }
    if (res.isError) {
      Left(res.error.asException)
    } else if (res.status != 200) Left(StrictFailureException(s"Returned non-200 response: [$res]"))
    else
      res.result match {
        case bulkResponse: BulkResponse if bulkResponse.hasFailures =>
          findBulkError(bulkResponse.items) match {
            case Some(err) => Left(err.asException)
            case None      => Left(StrictFailureException(s"Unknown bulk execution error in response $res"))
          }
        case other => Right(other)
      }
  }

}
