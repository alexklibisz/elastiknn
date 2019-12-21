package com.klibisz.elastiknn.query

import java.util
import java.util.Objects
import java.util.concurrent.Callable
import java.util.function.BiConsumer

import com.google.common.cache.CacheBuilder
import com.google.common.io.BaseEncoding
import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, IndexedQueryVector, LshQueryOptions, QueryOptions, QueryVector}
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.processor.StoredScripts
import com.klibisz.elastiknn.utils.CirceUtils._
import com.klibisz.elastiknn.utils.ProtobufUtils._
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.apache.logging.log4j.{LogManager, Logger}
import org.apache.lucene.search.Query
import org.apache.lucene.util.SetOnce
import org.elasticsearch.action.{ActionListener, ActionResponse}
import org.elasticsearch.action.get.{GetAction, GetRequest, GetResponse}
import org.elasticsearch.action.ingest.{GetPipelineAction, GetPipelineRequest}
import org.elasticsearch.client.Client
import org.elasticsearch.common.CheckedConsumer
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.lucene.search.function.{ScriptScoreFunction, ScriptScoreQuery}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.functionscore.ScriptScoreFunctionBuilder
import org.elasticsearch.index.query._
import org.elasticsearch.script.Script
import scalapb_circe.JsonFormat

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

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
}

final class KnnQueryBuilder(val query: KNearestNeighborsQuery) extends AbstractQueryBuilder[KnnQueryBuilder] {

  /** Encodes the KnnQueryBuilder to a StreamOutput as a base64 string. */
  override def doWriteTo(out: StreamOutput): Unit =
    out.writeString(query.toBase64)

  override def doRewrite(context: QueryRewriteContext): QueryBuilder =
    query.queryVector match {
      case QueryVector.Indexed(indexedQueryVector) => rewriteIndexed(context, indexedQueryVector)
      case QueryVector.Given(elastiKnnVector) =>
        query.queryOptions match {
          case QueryOptions.Exact(exactQueryOptions) => new KnnExactQueryBuilder(exactQueryOptions, elastiKnnVector)
          case QueryOptions.Lsh(lshQueryOptions)     => new LshQueryBuilder(lshQueryOptions, elastiKnnVector)
          case QueryOptions.Empty                    => throw illArgEx("Query options cannot be empty")
        }
      case QueryVector.Empty => throw illArgEx("Query vector cannot be empty")
    }

  /** Fetches the indexed vector from the cluster and rewrites the query as a given vector query. */
  private def rewriteIndexed(context: QueryRewriteContext, qv: IndexedQueryVector): QueryBuilder = {
    val supplier = new SetOnce[KnnQueryBuilder]()
    context.registerAsyncAction((c: Client, l: ActionListener[_]) => {
      c.execute(
        GetAction.INSTANCE,
        new GetRequest(qv.index, qv.id),
        new ActionListener[GetResponse] {
          def onResponse(res: GetResponse): Unit = {
            val pth = s"${qv.field}."
            val map = pth.split('.').foldLeft(res.getSourceAsMap) {
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

  val NAME = s"${ELASTIKNN_NAME}_knn_exact"

  object Parser extends QueryParser[KnnExactQueryBuilder] {
    override def fromXContent(parser: XContentParser): KnnExactQueryBuilder =
      throw new IllegalStateException(s"Use the ${KnnQueryBuilder.NAME} query directly")
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

final class LshQueryBuilder(lshQueryOptions: LshQueryOptions, elastiKnnVector: ElastiKnnVector)
    extends AbstractQueryBuilder[LshQueryBuilder] {
  override def doWriteTo(out: StreamOutput): Unit = ???

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ???

  override def doToQuery(context: QueryShardContext): Query = ???

  override def doEquals(other: LshQueryBuilder): Boolean = ???

  override def doHashCode(): Int = ???

  override def getWriteableName: String = ???
}
