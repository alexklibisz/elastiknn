package org.elasticsearch.elastiknn.query

import java.util
import java.util.Objects

import com.google.common.cache.{Cache, CacheBuilder}
import io.circe.parser._
import io.circe.syntax._
import org.apache.lucene.search.Query
import org.apache.lucene.util.SetOnce
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.get.{GetAction, GetRequest, GetResponse}
import org.elasticsearch.action.ingest.{GetPipelineAction, GetPipelineRequest, GetPipelineResponse}
import org.elasticsearch.client.Client
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.lucene.search.function.{FunctionScoreQuery, ScriptScoreFunction, ScriptScoreQuery}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.elastiknn.KNearestNeighborsQuery._
import org.elasticsearch.elastiknn.mapper.ElastiKnnVectorFieldMapper
import org.elasticsearch.elastiknn.models.VectorHashingModel
import org.elasticsearch.elastiknn.processor.StoredScripts
import org.elasticsearch.elastiknn.utils.CirceUtils._
import org.elasticsearch.elastiknn.utils.Implicits._
import org.elasticsearch.elastiknn.{KNearestNeighborsQuery, ProcessorOptions, Similarity, _}
import org.elasticsearch.index.mapper.MappedFieldType
import org.elasticsearch.index.query._
import org.elasticsearch.index.query.functionscore.ScriptScoreFunctionBuilder
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

  /** Used to store [[ProcessorOptions]] needed for LSH queries. */
  private val processorOptionsCache: Cache[LshQueryOptions, ProcessorOptions] =
    CacheBuilder.newBuilder.softValues.build[LshQueryOptions, ProcessorOptions]()

}

final class KnnQueryBuilder(val query: KNearestNeighborsQuery) extends AbstractQueryBuilder[KnnQueryBuilder] {

  import KnnQueryBuilder._

  /** Encodes the KnnQueryBuilder to a StreamOutput as a base64 string. */
  override def doWriteTo(out: StreamOutput): Unit =
    out.writeString(query.toBase64String)

  override def doRewrite(context: QueryRewriteContext): QueryBuilder =
    query.queryVector match {
      case QueryVector.Indexed(indexedQueryVector) => rewriteIndexed(context, indexedQueryVector)
      case QueryVector.Given(elastiKnnVector) =>
        query.queryOptions match {
          case QueryOptions.Lsh(lshQueryOptions)     => rewriteLsh(context, lshQueryOptions, elastiKnnVector)
          case QueryOptions.Exact(exactQueryOptions) => new KnnExactQueryBuilder(exactQueryOptions, ensureSorted(elastiKnnVector))
          case QueryOptions.Empty                    => throw illArgEx("Query options cannot be empty")
        }
      case QueryVector.Empty => throw illArgEx("Query vector cannot be empty")
    }

  /**
    * Ensures that a SparseBoolVector which wasn't indexed (i.e. was given by the user) has sorted indices.
    * This is an important optimization for computing similarities in O(d) time.
    * Otherwise assume that an indexed vector was already sorted when it was stored.
    */
  private def ensureSorted(ekv: ElastiKnnVector): ElastiKnnVector = ekv.vector match {
    case ElastiKnnVector.Vector.SparseBoolVector(sbv) => ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv.sorted()))
    case _                                            => ekv
  }

  /**
    * Attempt to load [[ProcessorOptions]] from cache. If not present, retrieve them from cluster cache them.
    * Then use the [[ProcessorOptions]] to instantiate a new [[KnnLshQueryBuilder]].
    */
  private def rewriteLsh(context: QueryRewriteContext, lshQueryOptions: LshQueryOptions, elastiKnnVector: ElastiKnnVector): QueryBuilder = {
    val cached: ProcessorOptions = processorOptionsCache.getIfPresent(lshQueryOptions)
    if (cached != null) KnnLshQueryBuilder(cached, elastiKnnVector)
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
              supplier.set(KnnLshQueryBuilder(procOpts, elastiKnnVector))
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
    // TODO: can you read the binary version of the document instead of the source?
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

final class KnnExactQueryBuilder(val exactQueryOptions: ExactQueryOptions,
                                 val queryVector: ElastiKnnVector,
                                 val subQueryBuilder: Option[QueryBuilder] = None)
    extends AbstractQueryBuilder[KnnExactQueryBuilder] {

  import exactQueryOptions._

  override def doToQuery(context: QueryShardContext): Query = {
    val subQuery: Query = subQueryBuilder.getOrElse(new ExistsQueryBuilder(fieldRaw)).toQuery(context)
    val fieldType: MappedFieldType = context.getMapperService.fullName(fieldRaw)
    val fieldData: ElastiKnnVectorFieldMapper.FieldData = context.getForField(fieldType)
    new FunctionScoreQuery(subQuery, KnnExactScoreFunction(similarity, queryVector, fieldData))
  }

  def doWriteTo(out: StreamOutput): Unit = {
    out.writeString(exactQueryOptions.toBase64String)
    out.writeString(queryVector.toBase64String)
  }

  def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  def doEquals(other: KnnExactQueryBuilder): Boolean =
    exactQueryOptions == other.exactQueryOptions && queryVector == other.queryVector

  def doHashCode(): Int = Objects.hash(exactQueryOptions, queryVector)

  def getWriteableName: String = KnnExactQueryBuilder.NAME
}

object KnnLshQueryBuilder {

  val NAME = s"${KnnQueryBuilder.NAME}_lsh"

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
        ElastiKnnVector.parseBase64(in.readString()),
        decode[Map[String, String]](in.readString()).toTry.get
      )
    }
  }

  /**
    * Instantiate a [[KnnLshQueryBuilder]] such that the given [[ElastiKnnVector]] is only hashed once.
    * Otherwise it would have to be hashed inside the [[KnnLshQueryBuilder]], which could happen on each node.
    * @param processorOptions
    * @param queryVector
    * @return
    */
  def apply(processorOptions: ProcessorOptions, queryVector: ElastiKnnVector): KnnLshQueryBuilder = {
    val hashed = VectorHashingModel
      .hash(processorOptions, queryVector)
      .recover {
        case t: Throwable => throw illArgEx(s"$queryVector could not be hashed", Some(t))
      }
      .get
    new KnnLshQueryBuilder(processorOptions, queryVector, hashed)
  }

}

final class KnnLshQueryBuilder private (val processorOptions: ProcessorOptions,
                                        val queryVector: ElastiKnnVector,
                                        val hashed: Map[String, String])
    extends AbstractQueryBuilder[KnnLshQueryBuilder] {

  def doWriteTo(out: StreamOutput): Unit = {
    out.writeString(processorOptions.toBase64String)
    out.writeString(queryVector.toBase64String)
    out.writeString(hashed.asJson.noSpaces)
  }

  def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  private val fieldProc: Try[String] = processorOptions.modelOptions.fieldProc match {
    case Some(fp) => Success(fp)
    case None     => Failure(illArgEx(s"${processorOptions.modelOptions} does not specify a processed field."))
  }

  private val similarity: Try[Similarity] = processorOptions.modelOptions.similarity match {
    case Some(sim) => Success(sim)
    case None      => Failure(illArgEx(s"${processorOptions.modelOptions} does not correspond to any similarity."))
  }

  /**
    * Constructs a [[org.apache.lucene.search.BooleanQuery]] for approximate searching by hashes. Then uses that query
    * in a [[ScriptScoreQuery]] which executes an exact query to refine the approximate results.
    * @param context
    * @return
    */
  def doToQuery(context: QueryShardContext): Query = {
    // TODO: is there a way to control the number of documents pass through from the bool query to the exact query?
    val queryTry = for {
      fp <- fieldProc
      boolQuery = hashed
        .foldLeft(new BoolQueryBuilder()) {
          case (b, (k, v)) => b.should(new TermQueryBuilder(s"$fp.$k", v))
        }
      similarity <- this.similarity
    } yield {
      val exactOpts = ExactQueryOptions(processorOptions.fieldRaw, similarity)
      new KnnExactQueryBuilder(exactOpts, queryVector, Some(boolQuery)).toQuery(context)
    }
    queryTry.get
  }

  def doEquals(other: KnnLshQueryBuilder): Boolean =
    processorOptions == other.processorOptions && hashed == other.hashed

  def doHashCode(): Int = Objects.hash(processorOptions, hashed)

  def getWriteableName: String = KnnLshQueryBuilder.NAME
}
