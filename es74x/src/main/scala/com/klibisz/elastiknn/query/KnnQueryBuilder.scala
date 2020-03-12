package com.klibisz.elastiknn.query

import java.util
import java.util.Objects

import com.google.common.cache.{Cache, CacheBuilder}
import com.klibisz.elastiknn.KNearestNeighborsQuery._
import com.klibisz.elastiknn.ProcessorOptions.{ExactComputedModelOptions, ExactIndexedModelOptions, JaccardLshModelOptions, ModelOptions}
import com.klibisz.elastiknn.Similarity.SIMILARITY_JACCARD
import com.klibisz.elastiknn.mapper.ElastiKnnVectorFieldMapper
import com.klibisz.elastiknn.models.ProcessedVector
import com.klibisz.elastiknn.utils.CirceUtils
import com.klibisz.elastiknn.utils.Utils._
import com.klibisz.elastiknn.{KNearestNeighborsQuery, ProcessorOptions, models, _}
import io.circe.syntax._
import org.apache.lucene.index.{LeafReaderContext, SortedNumericDocValues}
import org.apache.lucene.search.{Explanation, Query}
import org.apache.lucene.util.SetOnce
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.get.{GetAction, GetRequest, GetResponse}
import org.elasticsearch.action.ingest.{GetPipelineAction, GetPipelineRequest, GetPipelineResponse}
import org.elasticsearch.client.Client
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.lucene.search.function.{CombineFunction, FunctionScoreQuery, LeafScoreFunction, ScoreFunction}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.fielddata.plain.SortedNumericDVIndexFieldData
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

  private val procOptsCache: Cache[String, ProcessorOptions] = CacheBuilder.newBuilder.build[String, ProcessorOptions]()

}

/**
  * Main purpose of this class is to rewrite the given generic query to a more specific query builder.
  * @param query a KNearestNeighborsQuery from the end user.
  * @param processorOptions optional processor options, fetched using the query pipelineId in the rewrite phase.
  */
final class KnnQueryBuilder(val query: KNearestNeighborsQuery, processorOptions: Option[ProcessorOptions] = None)
    extends AbstractQueryBuilder[KnnQueryBuilder]
    with CirceUtils {

  override def doWriteTo(out: StreamOutput): Unit = out.writeString(query.toBase64)

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doToQuery(context: QueryShardContext): Query =
    throw new IllegalStateException("Query should have been re-written")

  override def doEquals(other: KnnQueryBuilder): Boolean = this.query == other.query

  override def doHashCode(): Int = Objects.hash(query)

  override def getWriteableName: String = KnnQueryBuilder.NAME

  private def ensureSorted(ekv: ElastiKnnVector): ElastiKnnVector = ekv.vector match {
    case ElastiKnnVector.Vector.SparseBoolVector(sbv) => ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv.sorted()))
    case other                                        => ElastiKnnVector(other)
  }

  override def doRewrite(context: QueryRewriteContext): QueryBuilder = query.queryVector match {
    case QueryVector.Indexed(iqv) => rewriteFetchQueryVector(context, iqv)
    case QueryVector.Given(ekv) =>
      processorOptions match {
        case None => rewriteFetchProcessorOptions(context)
        case Some(popts) =>
          (popts.modelOptions, query.queryOptions, ekv, models.processVector(popts, ekv)) match {
            // Exact computed for any similarity.
            case (
                ModelOptions.ExactComputed(mopts),
                QueryOptions.ExactComputed(qopts),
                _,
                Success(proc: ProcessedVector.ExactComputed)
                ) =>
              new ExactComputedQueryBuilder(mopts, qopts, ensureSorted(ekv), proc, popts.fieldRaw, query.useCache)
            // Exact indexed for Jaccard similarity.
            case (
                ModelOptions.ExactIndexed(mopts),
                QueryOptions.ExactIndexed(qopts),
                ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv)),
                Success(proc: ProcessedVector.ExactIndexedJaccard)
                ) =>
              new ExactIndexedJaccardQueryBuilder(mopts, qopts, sbv.sorted(), proc, popts.fieldRaw, query.useCache)
            // LSH for Jaccard similarity.
            case (
                ModelOptions.JaccardLsh(mopts),
                QueryOptions.JaccardLsh(qopts),
                ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv)),
                Success(proc: ProcessedVector.JaccardLsh)
                ) =>
              new JaccardLshQueryBuilder(mopts, qopts, sbv.sorted(), proc, popts.fieldRaw, query.useCache)
            case other =>
              throw new IllegalArgumentException(s"Cannot convert this combination to a valid query: $other")
          }
      }
    case QueryVector.Empty => throw new IllegalArgumentException(s"Query vector cannot be empty")
  }

  private def rewriteFetchQueryVector(context: QueryRewriteContext, iqv: KNearestNeighborsQuery.IndexedQueryVector): QueryBuilder = {
    // TODO: can you read the binary version of the document instead of the source?
    val supplier = new SetOnce[KnnQueryBuilder]()
    context.registerAsyncAction((c: Client, l: ActionListener[_]) => {
      c.execute(
        GetAction.INSTANCE,
        new GetRequest(iqv.index, iqv.id),
        new ActionListener[GetResponse] {
          def onResponse(response: GetResponse): Unit =
            extractNested(s"${iqv.field}.".split('.'), response.getSourceAsString)
              .map { json =>
                val ekv = JsonFormat.fromJson[ElastiKnnVector](json)
                supplier.set(new KnnQueryBuilder(query.withGiven(ekv), processorOptions))
                l.asInstanceOf[ActionListener[Any]].onResponse(null)
              }
              .recover {
                case t =>
                  val ex =
                    new RuntimeException(s"failed to find or parse vector index [${iqv.index}] id [${iqv.id}] field [${iqv.field}]", t)
                  l.onFailure(ex)
              }
              .get
          def onFailure(e: Exception): Unit = l.onFailure(e)
        }
      )
    })
    RewriteLater(_ => supplier.get())
  }

  private def rewriteFetchProcessorOptions(context: QueryRewriteContext): QueryBuilder = {
    val maybeCached: ProcessorOptions = KnnQueryBuilder.procOptsCache.getIfPresent(query.pipelineId)
    if (maybeCached != null) new KnnQueryBuilder(query, Some(maybeCached))
    else {
      // Put all the sketchy stuff in one place.
      def parseResponse(response: GetPipelineResponse): Try[ProcessorOptions] =
        Try {
          val processorOptsMap = response
            .pipelines()
            .asScala
            .find(_.getId == query.pipelineId)
            .getOrElse(throw illArgEx(s"Couldn't find pipeline with id ${query.pipelineId}"))
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
          case t: Throwable => Failure(illArgEx(s"Failed to find or parse pipeline with id ${query.pipelineId}", Some(t)))
        }

      val supplier = new SetOnce[KnnQueryBuilder]()
      context.registerAsyncAction((c: Client, l: ActionListener[_]) => {
        c.execute(
          GetPipelineAction.INSTANCE,
          new GetPipelineRequest(query.pipelineId),
          new ActionListener[GetPipelineResponse] {
            override def onResponse(response: GetPipelineResponse): Unit = {
              val processorOptions = parseResponse(response).get
              KnnQueryBuilder.procOptsCache.put(query.pipelineId, processorOptions)
              supplier.set(new KnnQueryBuilder(query, Some(processorOptions)))
              l.asInstanceOf[ActionListener[Any]].onResponse(null)
            }
            override def onFailure(e: Exception): Unit = l.onFailure(e)
          }
        )
      })
      RewriteLater(_ => supplier.get())
    }
  }
}

object ExactComputedQueryBuilder {

  val NAME = s"${KnnQueryBuilder.NAME}_exact_computed"

  object Parser extends QueryParser[ExactComputedQueryBuilder] {
    override def fromXContent(parser: XContentParser): ExactComputedQueryBuilder =
      throw new IllegalStateException(s"Use the ${KnnQueryBuilder.NAME} query instead")
  }

  object Reader extends Writeable.Reader[ExactComputedQueryBuilder] {
    override def read(in: StreamInput): ExactComputedQueryBuilder = {
      in.readFloat()
      in.readOptionalString()
      new ExactComputedQueryBuilder(
        modelOptions = ExactComputedModelOptions.parseBase64(in.readString()),
        queryOptions = ExactComputedQueryOptions.parseBase64(in.readString()),
        queryVector = ElastiKnnVector.parseBase64(in.readString()),
        processedVector = io.circe.parser.decode[ProcessedVector.ExactComputed](in.readString()).right.get,
        fieldRaw = in.readString(),
        useCache = in.readBoolean()
      )
    }
    def write(b: ExactComputedQueryBuilder, out: StreamOutput): Unit = {
      out.writeString(b.modelOptions.toBase64)
      out.writeString(b.queryOptions.toBase64)
      out.writeString(b.queryVector.toBase64)
      out.writeString(b.processedVector.asJson.noSpaces)
      out.writeString(b.fieldRaw)
      out.writeBoolean(b.useCache)
    }
  }

}

final class ExactComputedQueryBuilder(val modelOptions: ExactComputedModelOptions,
                                      val queryOptions: ExactComputedQueryOptions,
                                      val queryVector: ElastiKnnVector,
                                      val processedVector: ProcessedVector.ExactComputed,
                                      val fieldRaw: String,
                                      val useCache: Boolean)
    extends AbstractQueryBuilder[ExactComputedQueryBuilder] {

  override def doWriteTo(out: StreamOutput): Unit = ExactComputedQueryBuilder.Reader.write(this, out)

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  private val defaultSubquery = new ExistsQueryBuilder(fieldRaw)

  override def doToQuery(context: QueryShardContext): Query = {
    val ft: MappedFieldType = context.getMapperService.fullName(fieldRaw)
    val fd: ElastiKnnVectorFieldMapper.FieldData = context.getForField(ft)
    new FunctionScoreQuery(
      defaultSubquery.toQuery(context),
      new KnnExactScoreFunction(modelOptions.similarity, queryVector, fd, useCache, None)
    )
  }

  override def doEquals(other: ExactComputedQueryBuilder): Boolean =
    this.modelOptions == other.modelOptions &&
      this.queryOptions == other.queryOptions &&
      this.queryVector == other.queryVector &&
      this.processedVector == other.processedVector &&
      this.fieldRaw == other.fieldRaw &&
      this.useCache == other.useCache

  override def doHashCode(): Int = Objects.hashCode(
    modelOptions,
    queryOptions,
    queryVector,
    processedVector,
    fieldRaw,
    useCache.asInstanceOf[AnyRef]
  )

  override def getWriteableName: String = ExactComputedQueryBuilder.NAME
}

object ExactIndexedJaccardQueryBuilder {
  val NAME = s"${KnnQueryBuilder.NAME}_indexed_jaccard"

  object Parser extends QueryParser[ExactIndexedJaccardQueryBuilder] {
    override def fromXContent(parser: XContentParser): ExactIndexedJaccardQueryBuilder =
      throw new IllegalStateException(s"Use the ${KnnQueryBuilder.NAME} query instead")
  }

  object Reader extends Writeable.Reader[ExactIndexedJaccardQueryBuilder] {
    override def read(in: StreamInput): ExactIndexedJaccardQueryBuilder = {
      in.readFloat()
      in.readOptionalString()
      new ExactIndexedJaccardQueryBuilder(
        modelOptions = ExactIndexedModelOptions.parseBase64(in.readString()),
        queryOptions = ExactIndexedQueryOptions.parseBase64(in.readString()),
        queryVector = SparseBoolVector.parseBase64(in.readString()),
        processedQueryVector = io.circe.parser.decode[ProcessedVector.ExactIndexedJaccard](in.readString()).right.get,
        fieldRaw = in.readString(),
        useCache = in.readBoolean()
      )
    }

    def write(b: ExactIndexedJaccardQueryBuilder, out: StreamOutput): Unit = {
      out.writeString(b.modelOptions.toBase64)
      out.writeString(b.queryOptions.toBase64)
      out.writeString(b.queryVector.toBase64)
      out.writeString(b.processedQueryVector.asJson.noSpaces)
      out.writeString(b.fieldRaw)
      out.writeBoolean(b.useCache)
    }
  }

}

final class ExactIndexedJaccardQueryBuilder(val modelOptions: ExactIndexedModelOptions,
                                            val queryOptions: ExactIndexedQueryOptions,
                                            val queryVector: SparseBoolVector,
                                            val processedQueryVector: ProcessedVector.ExactIndexedJaccard,
                                            val fieldRaw: String,
                                            val useCache: Boolean)
    extends AbstractQueryBuilder[ExactIndexedJaccardQueryBuilder] {

  override def doWriteTo(out: StreamOutput): Unit = ExactIndexedJaccardQueryBuilder.Reader.write(this, out)

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doToQuery(context: QueryShardContext): Query = {
    val mq: MatchQueryBuilder =
      new MatchQueryBuilder(s"${modelOptions.fieldProcessed}.ExactIndexedJaccard.trueIndices", processedQueryVector.trueIndices)
    val ft: MappedFieldType = context.getMapperService.fullName(s"${modelOptions.fieldProcessed}.ExactIndexedJaccard.numTrueIndices")
    val fd: SortedNumericDVIndexFieldData = context.getForField(ft)
    val outerHashCode = this.hashCode()
    new FunctionScoreQuery(
      mq.toQuery(context),
      new ScoreFunction(CombineFunction.REPLACE) {
        override def needsScores(): Boolean = false
        override def doEquals(other: ScoreFunction): Boolean = this.hashCode == other.hashCode
        override def doHashCode(): Int = outerHashCode
        override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
          val values: SortedNumericDocValues = fd.load(ctx).getLongValues
          new LeafScoreFunction {
            override def score(docId: Int, intersection: Float): Double =
              if (values.advanceExact(docId)) {
                // TODO: the default FunctionScoreQuery behavior is to multiply the sub query score by the score returned
                // from the score function. In order to undo that, you have to divide the Jaccard by the intersection.
                // Surely there is a cleaner way, but I've tried all the ways I could think to instantiate the ScoreFunction.
                val storedNumTrueIndices = values.nextValue()
                val jacc = intersection / (processedQueryVector.numTrueIndices + storedNumTrueIndices - intersection)
                jacc / intersection
              } else throw new IllegalStateException(s"Couldn't read the number of true indices for document $docId in context $ctx")
            override def explainScore(docId: Int, subQueryScore: Explanation): Explanation = subQueryScore
          }
        }
      }
    )
  }

  override def doEquals(other: ExactIndexedJaccardQueryBuilder): Boolean =
    this.modelOptions == other.modelOptions &&
      this.queryOptions == other.queryOptions &&
      this.queryVector == other.queryVector &&
      this.processedQueryVector == other.processedQueryVector &&
      this.fieldRaw == other.fieldRaw &&
      this.useCache == other.useCache

  override def doHashCode(): Int =
    Objects.hash(modelOptions, queryOptions, queryVector, processedQueryVector, fieldRaw, useCache.asInstanceOf[AnyRef])

  override def getWriteableName: String = ExactIndexedJaccardQueryBuilder.NAME
}

object JaccardLshQueryBuilder {
  val NAME = s"${KnnQueryBuilder.NAME}_jaccard_lsh"

  object Parser extends QueryParser[JaccardLshQueryBuilder] {
    override def fromXContent(parser: XContentParser): JaccardLshQueryBuilder =
      throw new IllegalStateException(s"Use the ${KnnQueryBuilder.NAME} query instead")
  }

  object Reader extends Writeable.Reader[JaccardLshQueryBuilder] {
    override def read(in: StreamInput): JaccardLshQueryBuilder = {
      in.readFloat()
      in.readOptionalString()
      new JaccardLshQueryBuilder(
        modelOptions = JaccardLshModelOptions.parseBase64(in.readString()),
        queryOptions = JaccardLshQueryOptions.parseBase64(in.readString()),
        queryVector = SparseBoolVector.parseBase64(in.readString()),
        processedQueryVector = io.circe.parser.decode[ProcessedVector.JaccardLsh](in.readString()).right.get,
        fieldRaw = in.readString(),
        useCache = in.readBoolean()
      )
    }

    def write(b: JaccardLshQueryBuilder, out: StreamOutput): Unit = {
      out.writeString(b.modelOptions.toBase64)
      out.writeString(b.queryOptions.toBase64)
      out.writeString(b.queryVector.toBase64)
      out.writeString(b.processedQueryVector.asJson.noSpaces)
      out.writeString(b.fieldRaw)
      out.writeBoolean(b.useCache)
    }
  }

}

final class JaccardLshQueryBuilder(val modelOptions: JaccardLshModelOptions,
                                   val queryOptions: JaccardLshQueryOptions,
                                   val queryVector: SparseBoolVector,
                                   val processedQueryVector: ProcessedVector.JaccardLsh,
                                   val fieldRaw: String,
                                   val useCache: Boolean)
    extends AbstractQueryBuilder[JaccardLshQueryBuilder] {

  override def doWriteTo(out: StreamOutput): Unit = JaccardLshQueryBuilder.Reader.write(this, out)

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doToQuery(context: QueryShardContext): Query = {
    val mqb = new MatchQueryBuilder(s"${modelOptions.fieldProcessed}.JaccardLsh.hashes", processedQueryVector.hashes)
    val ft: MappedFieldType = context.getMapperService.fullName(fieldRaw)
    val fd: ElastiKnnVectorFieldMapper.FieldData = context.getForField(ft)
    new FunctionScoreQuery(
      mqb.toQuery(context),
      new KnnExactScoreFunction(SIMILARITY_JACCARD, ElastiKnnVector(queryVector), fd, useCache, Some(queryOptions.numCandidates))
    )
  }

  override def doEquals(other: JaccardLshQueryBuilder): Boolean =
    this.modelOptions == other.modelOptions &&
      this.queryOptions == other.queryOptions &&
      this.queryVector == other.queryVector &&
      this.processedQueryVector == other.processedQueryVector &&
      this.fieldRaw == other.fieldRaw &&
      this.useCache == other.useCache

  override def doHashCode(): Int =
    Objects.hash(modelOptions, queryOptions, queryVector, processedQueryVector, fieldRaw, useCache.asInstanceOf[AnyRef])

  override def getWriteableName: String = JaccardLshQueryBuilder.NAME
}
