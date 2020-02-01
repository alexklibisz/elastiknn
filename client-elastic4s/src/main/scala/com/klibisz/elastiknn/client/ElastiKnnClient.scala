package com.klibisz.elastiknn.client

import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.bulk.BulkResponse
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl, Executor, Handler}
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import com.klibisz.elastiknn.KNearestNeighborsQuery._
import com.klibisz.elastiknn.{ElastiKnnVector, ProcessorOptions}

import scala.concurrent.{ExecutionContext, Future}

final class ElastiKnnClient()(implicit elastic4sClient: ElasticClient, executionContext: ExecutionContext) extends AutoCloseable {

  import ElastiKnnDsl._
  import ElasticDsl._

  private def execute[T, U](req: T)(implicit
                                    handler: Handler[T, U],
                                    manifest: Manifest[U]): Future[U] =
    for {
      res <- elastic4sClient.execute(req)
      ret <- res match {
        case b: BulkResponse => if (b.hasFailures) Future.failed(b.error.asException) else Future.successful(b.result)
        case _               => if (res.isSuccess) Future.successful(res.result) else Future.failed(res.error.asException)
      }
    } yield ret

  /**
    * Create a pipeline for ingesting vectors.
    * @param pipelineId Id for the pipeline. You'll need to use this same id when ingesting vectors via this pipeline.
    * @param processorOptions See [[ProcessorOptions]].
    * @param pipelineDescription This doesn't have an important use-case, so it's optional.
    * @return
    */
  def createPipeline(pipelineId: String,
                     processorOptions: ProcessorOptions,
                     pipelineDescription: Option[String] = None): Future[PutPipelineResponse] =
    execute(
      PutPipelineRequest(
        pipelineId,
        pipelineDescription.getOrElse(s"Pipeline $pipelineId, created by the ElastiKnnClient"),
        Processor("elastiknn", processorOptions)
      ))

  /**
    * Index a set of vectors.
    * @param index
    * @param pipelineId
    * @param rawField
    * @param vectors
    * @param ids optional list of ids. There should be one per vector, otherwise they'll be ignored.
    * @param refresh if you want to immediately query for the vectors, set this to [[RefreshPolicy.Immediate]].
    * @return
    */
  def indexVectors(index: String,
                   pipelineId: String,
                   rawField: String,
                   vectors: Seq[ElastiKnnVector],
                   ids: Option[Seq[String]] = None,
                   refresh: RefreshPolicy = RefreshPolicy.None): Future[BulkResponse] = {
    val reqs = vectors.map(v => indexVector(index = index, rawField = rawField, vector = v, pipeline = Some(pipelineId)))
    val withIds: Seq[IndexRequest] = ids match {
      case Some(idsSeq) if (idsSeq.length == reqs.length) =>
        reqs.zip(idsSeq).map {
          case (req, id) => req.id(id)
        }
      case _ => reqs
    }
    execute(bulk(withIds).refresh(refresh))
  }

  def knnQuery(index: String, options: ExactQueryOptions, vector: ElastiKnnVector, k: Int): Future[SearchResponse] =
    execute(search(index).query(ElastiKnnDsl.knnQuery(options, vector)).size(k))

  def knnQuery(index: String, options: ExactQueryOptions, vector: IndexedQueryVector, k: Int): Future[SearchResponse] =
    execute(search(index).query(ElastiKnnDsl.knnQuery(options, vector)).size(k))

  def knnQuery(index: String, options: LshQueryOptions, vector: ElastiKnnVector, k: Int): Future[SearchResponse] =
    execute(search(index).query(ElastiKnnDsl.knnQuery(QueryOptions.Lsh(options), QueryVector.Given(vector))).size(k))

  def knnQuery(index: String, options: LshQueryOptions, vector: IndexedQueryVector, k: Int): Future[SearchResponse] =
    execute(search(index).query(ElastiKnnDsl.knnQuery(QueryOptions.Lsh(options), QueryVector.Indexed(vector))).size(k))

  def close(): Unit = elastic4sClient.close()
}

object ElastiKnnClient {

  def apply(host: HttpHost)(implicit ec: ExecutionContext): ElastiKnnClient = {
    implicit def fex: Executor[Future] = Executor.FutureExecutor(ec)
    val rc: RestClient = RestClient.builder(host).build()
    val jc: JavaClient = new JavaClient(rc)
    implicit val es: ElasticClient = ElasticClient(jc)
    new ElastiKnnClient()
  }

  def apply(hostname: String, port: Int)(implicit ec: ExecutionContext): ElastiKnnClient =
    ElastiKnnClient(new HttpHost(hostname, port))
}
