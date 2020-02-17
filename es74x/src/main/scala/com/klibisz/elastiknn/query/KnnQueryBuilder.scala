package com.klibisz.elastiknn.query

import java.util
import java.util.Objects

import com.google.common.cache.{Cache, CacheBuilder}
import com.klibisz.elastiknn.KNearestNeighborsQuery._
import com.klibisz.elastiknn.mapper.ElastiKnnVectorFieldMapper
import com.klibisz.elastiknn.mapper.ElastiKnnVectorFieldMapper.FieldData
import com.klibisz.elastiknn.models.VectorHashingModel
import com.klibisz.elastiknn.utils.Utils._
import com.klibisz.elastiknn.{KNearestNeighborsQuery, ProcessorOptions, Similarity, _}
import io.circe.parser._
import io.circe.syntax._
import org.apache.lucene.search.Query
import org.apache.lucene.util.SetOnce
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.get.{GetAction, GetRequest, GetResponse}
import org.elasticsearch.action.ingest.{GetPipelineAction, GetPipelineRequest, GetPipelineResponse}
import org.elasticsearch.client.Client
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.lucene.search.function.{FunctionScoreQuery, ScriptScoreQuery}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.mapper.MappedFieldType
import org.elasticsearch.index.query._
import scalapb_circe.JsonFormat

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object KnnQueryBuilder {

  val NAME = s"${ELASTIKNN_NAME}_knn"

  object Parser extends QueryParser[KnnQueryBuilder] {

    /** This is the first method that gets hit when you run this query. */
    override def fromXContent(parser: XContentParser): KnnQueryBuilder = {
      // TODO: why does parser.map() work here, but parser.text() throws an exception?
      val json = parser.map.asJson(javaMapEncoder)
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
          case QueryOptions.Lsh(lshQueryOptions) =>
            KnnLshQueryBuilder.rewrite(context, lshQueryOptions, ensureSorted(elastiKnnVector), query.useCache)
          case QueryOptions.Exact(exactQueryOptions) =>
            new KnnExactQueryBuilder(exactQueryOptions, ensureSorted(elastiKnnVector), query.useCache)
          case QueryOptions.Empty => throw illArgEx("Query options cannot be empty")
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
            val json = map.asJson(javaMapEncoder)
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
        ElastiKnnVector.parseBase64(in.readString()),
        in.readBoolean()
      )
    }
  }

}

final class KnnExactQueryBuilder(val exactQueryOptions: ExactQueryOptions, val queryVector: ElastiKnnVector, val useCache: Boolean)
    extends AbstractQueryBuilder[KnnExactQueryBuilder] {

  import exactQueryOptions._

  private lazy val defaultSubQuery = new ExistsQueryBuilder(fieldRaw)

  override def doToQuery(context: QueryShardContext): Query = {
    val subQuery: Query = defaultSubQuery.toQuery(context)
    val fieldType: MappedFieldType = context.getMapperService.fullName(fieldRaw)
    val fieldData: ElastiKnnVectorFieldMapper.FieldData = context.getForField(fieldType)
    new FunctionScoreQuery(subQuery, new KnnExactScoreFunction(similarity, queryVector, fieldData, useCache))
  }

  def doWriteTo(out: StreamOutput): Unit = {
    out.writeString(exactQueryOptions.toBase64)
    out.writeString(queryVector.toBase64)
    out.writeBoolean(useCache)
  }

  def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  def doEquals(that: KnnExactQueryBuilder): Boolean =
    this.exactQueryOptions == that.exactQueryOptions &&
      this.queryVector == that.queryVector &&
      this.useCache == that.useCache

  def doHashCode(): Int = Objects.hash(exactQueryOptions, queryVector, useCache.asInstanceOf[AnyRef])

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
        in.readString(),
        in.readBoolean()
      )
    }
  }

  /** Used to store [[ProcessorOptions]] needed for LSH queries. */
  private val processorOptionsCache: Cache[LshQueryOptions, ProcessorOptions] =
    CacheBuilder.newBuilder.softValues.build[LshQueryOptions, ProcessorOptions]()

  /**
    * Attempt to load [[ProcessorOptions]] from cache. If not present, retrieve them from cluster and cache them.
    * Then use the [[ProcessorOptions]] to instantiate a new [[KnnLshQueryBuilder]].
    */
  def rewrite(context: QueryRewriteContext,
              lshQueryOptions: LshQueryOptions,
              queryVector: ElastiKnnVector,
              useCache: Boolean): QueryBuilder = {
    val cached: ProcessorOptions = processorOptionsCache.getIfPresent(lshQueryOptions)
    if (cached != null) KnnLshQueryBuilder(cached, queryVector, useCache)
    else {
      // Put all the sketchy stuff in one place.
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
          JsonFormat.fromJson[ProcessorOptions](processorOptsMap.asJson(javaMapEncoder))
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
              supplier.set(KnnLshQueryBuilder(procOpts, queryVector, useCache))
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

  /**
    * Instantiate a [[KnnLshQueryBuilder]] such that the given [[ElastiKnnVector]] is only hashed once.
    * Otherwise it would have to be hashed inside the [[KnnLshQueryBuilder]], which could happen on each node.
    * @return
    */
  def apply(processorOptions: ProcessorOptions, queryVector: ElastiKnnVector, useCache: Boolean): KnnLshQueryBuilder = {
    val hashed = VectorHashingModel
      .hash(processorOptions, queryVector)
      .recover {
        case t: Throwable => throw illArgEx(s"$queryVector could not be hashed", Some(t))
      }
      .get
    new KnnLshQueryBuilder(processorOptions, queryVector, hashed, useCache)
  }

}

final class KnnLshQueryBuilder(val processorOptions: ProcessorOptions,
                               val queryVector: ElastiKnnVector,
                               val hashed: String,
                               val useCache: Boolean)
    extends AbstractQueryBuilder[KnnLshQueryBuilder] {

  def doWriteTo(out: StreamOutput): Unit = {
    out.writeString(processorOptions.toBase64)
    out.writeString(queryVector.toBase64)
    out.writeString(hashed)
    out.writeBoolean(useCache)
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
    * Constructs a match query for approximate searching by hashes. Then uses that query in a [[ScriptScoreQuery]]
    * which executes an exact query to refine the approximate results.
    */
  def doToQuery(context: QueryShardContext): Query = {
    val queryTry = for {
      fp <- fieldProc
      matchQuery = new MatchQueryBuilder(fp, hashed).analyzer("whitespace")
      similarity <- this.similarity
    } yield {
      val fieldType: MappedFieldType = context.getMapperService.fullName(processorOptions.fieldRaw)
      val fieldData: FieldData = context.getForField(fieldType)
      new FunctionScoreQuery(matchQuery.toQuery(context), new KnnExactScoreFunction(similarity, queryVector, fieldData, useCache))
    }
    queryTry.get
  }

  def doEquals(that: KnnLshQueryBuilder): Boolean =
    this.processorOptions == that.processorOptions &&
      this.queryVector == that.queryVector &&
      this.hashed == that.hashed &&
      this.useCache == that.useCache

  def doHashCode(): Int = Objects.hash(processorOptions, queryVector, hashed, useCache.asInstanceOf[AnyRef])

  def getWriteableName: String = KnnLshQueryBuilder.NAME
}
