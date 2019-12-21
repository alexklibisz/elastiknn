package com.klibisz.elastiknn.query

import java.util
import java.util.Objects

import com.google.common.cache.{Cache, CacheBuilder}
import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, IndexedQueryVector, LshQueryOptions, QueryOptions, QueryVector}
import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn.processor.StoredScripts
import com.klibisz.elastiknn.utils.CirceUtils._
import com.klibisz.elastiknn.utils.ProtobufUtils._
import com.klibisz.elastiknn.{KNearestNeighborsQuery, _}
import io.circe.syntax._
import org.apache.lucene.search.Query
import org.apache.lucene.util.SetOnce
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.get.{GetAction, GetRequest, GetResponse}
import org.elasticsearch.action.ingest.{GetPipelineAction, GetPipelineRequest, GetPipelineResponse}
import org.elasticsearch.client.Client
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.lucene.search.function.{ScriptScoreFunction, ScriptScoreQuery}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query._
import org.elasticsearch.index.query.functionscore.ScriptScoreFunctionBuilder
import org.elasticsearch.script.Script
import scalapb_circe.JsonFormat

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

object KnnQueryBuilder {

  val NAME = s"${ELASTIKNN_NAME}_knn"

  object Parser extends QueryParser[KnnQueryBuilder] {

    /** This is the first method that gets hit when you run this query. */
    override def fromXContent(parser: XContentParser): KnnQueryBuilder = {
      // TODO: why does parser.map() work here, but parser.text() throws an exception?
      val json = parser.map.asJson(mapEncoder)
      val query = JsonFormat.fromJson[KNearestNeighborsQuery](json)
      new KnnQueryBuilder(query)
    }

  }

  object Reader extends Writeable.Reader[KnnQueryBuilder] {

    /** This is uses to transfer the query across nodes in the cluster. */
    override def read(in: StreamInput): KnnQueryBuilder = {
      // https://github.com/elastic/elasticsearch/blob/master/server/src/main/java/org/elasticsearch/index/query/AbstractQueryBuilder.java#L66-L68
      in.readFloat()
      in.readOptionalString()
      new KnnQueryBuilder(KNearestNeighborsQuery.parseBase64(in.readString()))
    }
  }

  private val processorOptionsCache: Cache[LshQueryOptions, ProcessorOptions] =
    CacheBuilder.newBuilder.softValues.build[LshQueryOptions, ProcessorOptions]()

}

final class KnnQueryBuilder(val query: KNearestNeighborsQuery) extends AbstractQueryBuilder[KnnQueryBuilder] {

  import KnnQueryBuilder._

  /** Encodes the KnnQueryBuilder to a StreamOutput as a base64 string. */
  override def doWriteTo(out: StreamOutput): Unit =
    out.writeString(query.toBase64)

  override def doRewrite(context: QueryRewriteContext): QueryBuilder =
    query.queryVector match {
      case QueryVector.Indexed(indexedQueryVector) => rewriteIndexed(context, indexedQueryVector)
      case QueryVector.Given(elastiKnnVector) =>
        query.queryOptions match {
          case QueryOptions.Lsh(lshQueryOptions)     => rewriteLsh(context, lshQueryOptions, elastiKnnVector)
          case QueryOptions.Exact(exactQueryOptions) => new KnnExactQueryBuilder(exactQueryOptions, elastiKnnVector)
          case QueryOptions.Empty                    => throw illArgEx("Query options cannot be empty")
        }
      case QueryVector.Empty => throw illArgEx("Query vector cannot be empty")
    }

  /**
    * Attempt to load [[ProcessorOptions]] from cache. If not present, retrieve them from cluster cache them.
    * Then use the [[ProcessorOptions]] to instantiate a new [[KnnLshQueryBuilder]].
    */
  private def rewriteLsh(context: QueryRewriteContext, lshQueryOptions: LshQueryOptions, elastiKnnVector: ElastiKnnVector): QueryBuilder = {
    val cached: ProcessorOptions = processorOptionsCache.getIfPresent(lshQueryOptions)
    if (cached != null) new KnnLshQueryBuilder(cached, elastiKnnVector)
    else {
      // Put all the dangerous stuff in one place.
      def parseResponse(response: GetPipelineResponse): Try[ProcessorOptions] =
        Try {
          val processorOptsMap = response
            .pipelines()
            .asScala
            .find(_.getId == lshQueryOptions.pipelineId)
            .getOrElse(throw illArgEx(s"Couldn't find pipeline with id ${lshQueryOptions.pipelineId}"))
            .getConfigAsMap
            .get("processors")
            .asInstanceOf[util.List[util.Map[String, AnyRef]]]
            .asScala
            .find(_.containsKey(ELASTIKNN_NAME))
            .getOrElse(throw illArgEx(s"Couldn't find a processor with id $ELASTIKNN_NAME"))
            .get(ELASTIKNN_NAME)
            .asInstanceOf[util.Map[String, AnyRef]]
          JsonFormat.fromJson[ProcessorOptions](processorOptsMap.asJson(mapEncoder))
        }.recoverWith {
          case t: Throwable => Failure(illArgEx(s"Failed to find or parse pipeline with id ${lshQueryOptions.pipelineId}", Some(t)))
        }

      val supplier = new SetOnce[KnnLshQueryBuilder]()
      context.registerAsyncAction((c: Client, l: ActionListener[_]) => {
        c.execute(
          GetPipelineAction.INSTANCE,
          new GetPipelineRequest(lshQueryOptions.pipelineId),
          new ActionListener[GetPipelineResponse] {
            override def onResponse(response: GetPipelineResponse): Unit = {
              val procOpts = parseResponse(response).get
              supplier.set(new KnnLshQueryBuilder(procOpts, elastiKnnVector))
              processorOptionsCache.put(lshQueryOptions, procOpts)
              l.asInstanceOf[ActionListener[Any]].onResponse(null)
            }
            override def onFailure(e: Exception): Unit = l.onFailure(e)
          }
        )
      })
      RewriteLater(_ => supplier.get())
    }
  }

  /** Fetches the indexed vector from the cluster and rewrites the query as a given vector query. */
  private def rewriteIndexed(context: QueryRewriteContext, qv: IndexedQueryVector): QueryBuilder = {
    val supplier = new SetOnce[KnnQueryBuilder]()
    context.registerAsyncAction((c: Client, l: ActionListener[_]) => {
      c.execute(
        GetAction.INSTANCE,
        new GetRequest(qv.index, qv.id),
        new ActionListener[GetResponse] {
          def onResponse(response: GetResponse): Unit = {
            val pth = s"${qv.field}."
            val map = pth.split('.').foldLeft(response.getSourceAsMap) {
              case (m, k) =>
                m.get(k).asInstanceOf[util.Map[String, AnyRef]]
            }
            val json = map.asJson(mapEncoder)
            val ekv = JsonFormat.fromJson[ElastiKnnVector](json)
            supplier.set(new KnnQueryBuilder(query.withGiven(ekv)))
            l.asInstanceOf[ActionListener[Any]].onResponse(null)
          }
          def onFailure(e: Exception): Unit = l.onFailure(e)
        }
      )
    })
    RewriteLater(_ => supplier.get())
  }

  override def doToQuery(context: QueryShardContext): Query =
    throw new IllegalArgumentException("Query should have been re-written")

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doEquals(other: KnnQueryBuilder): Boolean = this.query == other.query

  override def doHashCode(): Int = Objects.hash(this.query)

  override def getWriteableName: String = KnnQueryBuilder.NAME

}

object KnnExactQueryBuilder {

  val NAME = s"${KnnQueryBuilder.NAME}_exact"

  object Parser extends QueryParser[KnnExactQueryBuilder] {
    override def fromXContent(parser: XContentParser): KnnExactQueryBuilder =
      throw new IllegalStateException(s"Use the ${KnnQueryBuilder.NAME} query instead")
  }

  object Reader extends Writeable.Reader[KnnExactQueryBuilder] {
    override def read(in: StreamInput): KnnExactQueryBuilder = {
      in.readFloat()
      in.readOptionalString()
      new KnnExactQueryBuilder(
        ExactQueryOptions.parseBase64(in.readString()),
        ElastiKnnVector.parseBase64(in.readString())
      )
    }
  }

}

final class KnnExactQueryBuilder(val exactQueryOptions: ExactQueryOptions, val elastiKnnVector: ElastiKnnVector)
    extends AbstractQueryBuilder[KnnExactQueryBuilder] {

  import exactQueryOptions._

  override def doToQuery(context: QueryShardContext): Query = {
    import ElastiKnnVector.Vector._
    (similarity, elastiKnnVector.vector) match {
      case (SIMILARITY_ANGULAR, dvec: FloatVector) =>
        scriptScoreQuery(context, fieldRaw, StoredScripts.exactAngular.script(fieldRaw, dvec))
      case (SIMILARITY_L1, dvec: FloatVector) =>
        scriptScoreQuery(context, fieldRaw, StoredScripts.exactL1.script(fieldRaw, dvec))
      case (SIMILARITY_L2, dvec: FloatVector) =>
        scriptScoreQuery(context, fieldRaw, StoredScripts.exactL2.script(fieldRaw, dvec))
      case (SIMILARITY_HAMMING, bvec: SparseBoolVector) =>
        scriptScoreQuery(context, fieldRaw, StoredScripts.exactHamming.script(fieldRaw, bvec))
      case (SIMILARITY_JACCARD, bvec: SparseBoolVector) =>
        scriptScoreQuery(context, fieldRaw, StoredScripts.exactJaccard.script(fieldRaw, bvec))
      case (_, Empty) => throw illArgEx("Must provide vector")
      case (_, _)     => throw SimilarityAndTypeException(similarity, elastiKnnVector)
    }
  }

  private def scriptScoreQuery(context: QueryShardContext, field: String, script: Script): ScriptScoreQuery = {
    val exists = new ExistsQueryBuilder(field).toQuery(context)
    val function = new ScriptScoreFunctionBuilder(script).toFunction(context)
    new ScriptScoreQuery(exists, function.asInstanceOf[ScriptScoreFunction], 0.0f)
  }

  def doWriteTo(out: StreamOutput): Unit = {
    out.writeString(exactQueryOptions.toBase64)
    out.writeString(elastiKnnVector.toBase64)
  }

  def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  def doEquals(other: KnnExactQueryBuilder): Boolean =
    exactQueryOptions == other.exactQueryOptions && elastiKnnVector == other.elastiKnnVector

  def doHashCode(): Int = Objects.hash(exactQueryOptions, elastiKnnVector)

  def getWriteableName: String = KnnExactQueryBuilder.NAME
}

object KnnLshQueryBuilder {

  val NAME = s"${KnnQueryBuilder.NAME}_exact"

  object Parser extends QueryParser[KnnLshQueryBuilder] {
    override def fromXContent(parser: XContentParser): KnnLshQueryBuilder =
      throw new IllegalStateException(s"Use the ${KnnQueryBuilder.NAME} query instead")
  }

  object Reader extends Writeable.Reader[KnnLshQueryBuilder] {
    override def read(in: StreamInput): KnnLshQueryBuilder = {
      in.readFloat()
      in.readOptionalString()
      new KnnLshQueryBuilder(
        ProcessorOptions.parseBase64(in.readString()),
        ElastiKnnVector.parseBase64(in.readString())
      )
    }
  }

}

final class KnnLshQueryBuilder(val processorOptions: ProcessorOptions, val elastiKnnVector: ElastiKnnVector)
    extends AbstractQueryBuilder[KnnLshQueryBuilder] {

  def doWriteTo(out: StreamOutput): Unit = {
    out.writeString(processorOptions.toBase64)
    out.writeString(elastiKnnVector.toBase64)
  }

  def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  def doToQuery(context: QueryShardContext): Query = {
    ???
  }

  def doEquals(other: KnnLshQueryBuilder): Boolean =
    processorOptions == other.processorOptions && elastiKnnVector == other.elastiKnnVector

  def doHashCode(): Int = Objects.hash(processorOptions, elastiKnnVector)

  def getWriteableName: String = KnnLshQueryBuilder.NAME
}
