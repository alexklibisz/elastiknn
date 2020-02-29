package com.klibisz.elastiknn.client

import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.bulk.BulkResponse
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl, Executor, Handler}
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.requests._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Client used to prepare, store, and search vectors using the ElastiKnn plugin.
  * This uses elastic4s under the hood and is slightly more "opinionated" than the methods in [[Elastic4sUtils]].
  * So if you want lower-level methods, see [[Elastic4sUtils]].
  *
  * @param elastic4sClient A client provided by the elastic4s library.
  * @param executionContext The execution context where [[Future]]s are executed.
  */
final class ElastiKnnClient()(implicit elastic4sClient: ElasticClient, executionContext: ExecutionContext) extends AutoCloseable {

  import Elastic4sUtils._
  import ElasticDsl._

  private def execute[T, U](req: T)(implicit handler: Handler[T, U], manifest: Manifest[U]): Future[U] =
    for {
      res <- elastic4sClient.execute(req)
      ret <- res match {
        case b: BulkResponse => if (b.hasFailures) Future.failed(b.error.asException) else Future.successful(b.result)
        case _               => if (res.isSuccess) Future.successful(res.result) else Future.failed(res.error.asException)
      }
    } yield ret

  /**
    * Updates the index's mapping to support ElastiKnn types according to the given options.
    * @param index The index.
    * @param processorOptions The processor options.
    * @param _type The document type.
    * @return
    */
  def prepareMapping(index: String, processorOptions: ProcessorOptions, _type: String = "_doc"): Future[AcknowledgedResponse] =
    execute(PrepareMappingRequest(index, processorOptions, _type))

  /**
    * Create a pipeline for ingesting vectors.
    * @param pipelineId Id for the pipeline. You'll need to use this same id when ingesting vectors via this pipeline.
    * @param processorOptions See [[ProcessorOptions]].
    * @param pipelineDescription This doesn't have an important use-case, so it's optional.
    * @return
    */
  def createPipeline(pipelineId: String,
                     processorOptions: ProcessorOptions,
                     pipelineDescription: Option[String] = None): Future[AcknowledgedResponse] =
    execute(
      PutPipelineRequest(
        pipelineId,
        pipelineDescription.getOrElse(s"Pipeline $pipelineId, created by the ElastiKnnClient"),
        Processor("elastiknn", processorOptions)
      ))

  /**
    * Index a set of vectors. It's better to use this with batches of vectors rather than single vectors.
    *
    * @param index The index where vectors are stored.
    * @param pipelineId The pipeline used to process the vectors. Corresponds to the pipelineId used for [[ElastiKnnClient.createPipeline]].
    * @param rawField The name of the field where raw vector data is stored in each document.
    * @param vectors A seq of vector-like objects.
    * @param ids optional list of ids. There should be one per vector, otherwise they'll be ignored.
    * @param refresh if you want to immediately query for the vectors, set this to [[RefreshPolicy.Immediate]].
    * @tparam V A vector-like type implementing the [[ElastiKnnVectorLike]] typeclass.
    * @return Returns the elastic4s [[BulkResponse]] resulting from indexing the vectors.
    */
  def indexVectors[V: ElastiKnnVectorLike](index: String,
                                           pipelineId: String,
                                           rawField: String,
                                           vectors: Seq[V],
                                           ids: Option[Seq[String]] = None,
                                           refresh: RefreshPolicy = RefreshPolicy.None): Future[BulkResponse] = {
    val reqs = vectors.map(v => indexVector(index = index, rawField = rawField, vector = v, pipeline = Some(pipelineId)))
    val withIds: Seq[IndexRequest] = ids match {
      case Some(idsSeq) if idsSeq.length == reqs.length =>
        reqs.zip(idsSeq).map {
          case (req, id) => req.id(id)
        }
      case _ => reqs
    }
    execute(bulk(withIds).refresh(refresh))
  }

  /**
    * Run a K-Nearest-Neighbor query.
    *
    * @param index The index against which you're searching.
    * @param options An query-option-like object implementing the [[QueryOptionsLike]] typeclass. This defines some
    *                elastiknn-specific options about your search request, like whether it's an exact or LSH search.
    * @param queryVector A query-vector-like object implementing the [[QueryVectorLike]] typeclass. This is typically
    *                    a vector given explicitly (using [[KNearestNeighborsQuery.QueryVector.Given]] or a reference to
    *                    an already-indexed vector (using [[KNearestNeighborsQuery.QueryVector.Indexed]].
    * @param k The number of search hits to return.
    * @param useCache Corresponds to [[KNearestNeighborsQuery.useCache].
    * @tparam O A query-option-like type implementing the [[QueryOptionsLike]] typeclass.
    * @tparam V A query-vector-like type implementing the [[QueryVectorLike]] typeclass.
    * @return Returns the elastic4s [[SearchResponse]].
    */
  def knnQuery[O: QueryOptionsLike, V: QueryVectorLike](index: String,
                                                        options: O,
                                                        queryVector: V,
                                                        k: Int,
                                                        useCache: Boolean = false): Future[SearchResponse] =
    execute(search(index).query(Elastic4sUtils.knnQuery(options, queryVector, useCache)).size(k).fetchSource(false))

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

  def apply(hostname: String = "localhost", port: Int = 9200)(implicit ec: ExecutionContext): ElastiKnnClient =
    ElastiKnnClient(new HttpHost(hostname, port))
}
