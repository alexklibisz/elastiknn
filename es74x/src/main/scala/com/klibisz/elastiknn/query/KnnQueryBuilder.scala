package com.klibisz.elastiknn.query

import java.util
import java.util.Objects

import com.google.common.cache.{Cache, CacheBuilder}
import com.klibisz.elastiknn.KNearestNeighborsQuery._
import com.klibisz.elastiknn.ProcessorOptions.{ExactComputedModelOptions, ExactIndexedModelOptions, JaccardLshModelOptions, ModelOptions}
import com.klibisz.elastiknn.Similarity.SIMILARITY_JACCARD
import com.klibisz.elastiknn.mapper.ElastiKnnVectorFieldMapper
import com.klibisz.elastiknn.mapper.ElastiKnnVectorFieldMapper.FieldData
import com.klibisz.elastiknn.models.ProcessedVector
import com.klibisz.elastiknn.utils.Utils._
import com.klibisz.elastiknn.{KNearestNeighborsQuery, ProcessorOptions, Similarity, models, query, _}
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
import scala.reflect.ClassTag
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
    extends AbstractQueryBuilder[KnnQueryBuilder] {

  override def doWriteTo(out: StreamOutput): Unit = out.writeString(query.toBase64)

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doToQuery(context: QueryShardContext): Query =
    throw new IllegalStateException("Query should have been re-written")

  override def doEquals(other: KnnQueryBuilder): Boolean = this.query == other.query

  override def doHashCode(): Int = Objects.hash(query)

  override def getWriteableName: String = KnnQueryBuilder.NAME

  override def doRewrite(context: QueryRewriteContext): QueryBuilder = query.queryVector match {
    case QueryVector.Indexed(iqv) => rewriteFetchQueryVector(context, iqv)
    case QueryVector.Given(ekv) =>
      processorOptions match {
        case None => rewriteFetchProcessorOptions(context)
        case Some(popts) =>
          (popts.modelOptions, query.queryOptions, ekv, models.processVector(popts, ekv)) match {
            case (
                ModelOptions.ExactComputed(mopts),
                QueryOptions.ExactComputed(qopts),
                _,
                Success(proc: ProcessedVector.ExactComputed)
                ) =>
              new ExactComputedQueryBuilder(mopts, qopts, ekv, proc, popts.fieldRaw, query.useCache)
            case (
                ModelOptions.ExactIndexed(mopts),
                QueryOptions.ExactIndexed(qopts),
                ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv)),
                Success(proc: ProcessedVector.ExactIndexedJaccard)
                ) =>
              new ExactIndexedJaccardQueryBuilder(mopts, qopts, sbv, proc, popts.fieldRaw, query.useCache)
            case (
                ModelOptions.JaccardLsh(mopts),
                QueryOptions.JaccardLsh(qopts),
                ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv)),
                Success(proc: ProcessedVector.JaccardLsh)
                ) =>
              new JaccardLshQueryBuilder(mopts, qopts, sbv, proc, popts.fieldRaw, query.useCache)
            case other => throw new IllegalArgumentException(s"Cannot convert this combination to a valid query: $other")
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
          def onResponse(response: GetResponse): Unit = {
            val pth = s"${iqv.field}."
            val map = pth.split('.').foldLeft(response.getSourceAsMap) {
              case (m, k) =>
                m.get(k).asInstanceOf[util.Map[String, AnyRef]]
            }
            val json = map.asJson(javaMapEncoder)
            val ekv = JsonFormat.fromJson[ElastiKnnVector](json)
            supplier.set(new KnnQueryBuilder(query.withGiven(ekv), processorOptions))
            l.asInstanceOf[ActionListener[Any]].onResponse(null)
          }
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
    val ft = context.getMapperService.fullName(fieldRaw)
    val fd = context.getForField(ft)
    new FunctionScoreQuery(
      defaultSubquery.toQuery(context),
      new KnnExactScoreFunction(queryOptions.similarity, queryVector, fd, useCache, None)
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

final class ExactIndexedJaccardQueryBuilder(val modelOptions: ExactIndexedModelOptions,
                                            val queryOptions: ExactIndexedQueryOptions,
                                            val queryVector: SparseBoolVector,
                                            val processedVector: ProcessedVector.ExactIndexedJaccard,
                                            val fieldRaw: String,
                                            val useCache: Boolean)
    extends AbstractQueryBuilder[ExactIndexedJaccardQueryBuilder] {
  override def doWriteTo(out: StreamOutput): Unit = ???

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ???

  override def doToQuery(context: QueryShardContext): Query = ???

  override def doEquals(other: ExactIndexedJaccardQueryBuilder): Boolean = ???

  override def doHashCode(): Int = ???

  override def getWriteableName: String = ???
}

final class JaccardLshQueryBuilder(val modelOptions: JaccardLshModelOptions,
                                   val queryOptions: JaccardLshQueryOptions,
                                   val queryVector: SparseBoolVector,
                                   val processedVector: ProcessedVector.JaccardLsh,
                                   val fieldRaw: String,
                                   val useCache: Boolean)
    extends AbstractQueryBuilder[JaccardLshQueryBuilder] {
  override def doWriteTo(out: StreamOutput): Unit = ???

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ???

  override def doToQuery(context: QueryShardContext): Query = ???

  override def doEquals(other: JaccardLshQueryBuilder): Boolean = ???

  override def doHashCode(): Int = ???

  override def getWriteableName: String = ???
}

//object KnnProcessedQueryBuilder {
//
//  val NAME = s"${KnnQueryBuilder.NAME}_lsh"
//
//  object Parser extends QueryParser[KnnProcessedQueryBuilder] {
//    override def fromXContent(parser: XContentParser): KnnProcessedQueryBuilder =
//      throw new IllegalStateException(s"Use the ${KnnQueryBuilder.NAME} query instead")
//  }
//
//  object Reader extends Writeable.Reader[KnnProcessedQueryBuilder] {
//    override def read(in: StreamInput): KnnProcessedQueryBuilder = {
//      in.readFloat()
//      in.readOptionalString()
//      new KnnProcessedQueryBuilder(
//        ProcessorOptions.parseBase64(in.readString()),
//        ElastiKnnVector.parseBase64(in.readString()),
//        io.circe.parser.decode[models.ProcessedVector](in.readString()).right.get,
//        in.readInt(),
//        in.readBoolean()
//      )
//    }
//  }
//
//  /** Used to store [[ProcessorOptions]] needed for LSH queries. */
//  private val processorOptionsCache: Cache[String, ProcessorOptions] =
//    CacheBuilder.newBuilder.softValues.build[String, ProcessorOptions]()
//
//  /**
//    * Attempt to load [[ProcessorOptions]] from cache. If not present, retrieve them from cluster and cache them.
//    * Then use the [[ProcessorOptions]] to instantiate a new [[KnnProcessedQueryBuilder]].
//    */
//  def rewrite(context: QueryRewriteContext,
//              lshQueryOptions: JaccardLshQueryOptions,
//              queryVector: ElastiKnnVector,
//              useCache: Boolean): QueryBuilder = {
//    val cached: ProcessorOptions = processorOptionsCache.getIfPresent(lshQueryOptions.pipelineId)
//    if (cached != null) KnnProcessedQueryBuilder(cached, queryVector, lshQueryOptions.numCandidates, useCache)
//    else {
//      // Put all the sketchy stuff in one place.
//      def parseResponse(response: GetPipelineResponse): Try[ProcessorOptions] =
//        Try {
//          val processorOptsMap = response
//            .pipelines()
//            .asScala
//            .find(_.getId == lshQueryOptions.pipelineId)
//            .getOrElse(throw illArgEx(s"Couldn't find pipeline with id ${lshQueryOptions.pipelineId}"))
//            .getConfigAsMap
//            .get("processors")
//            .asInstanceOf[util.List[util.Map[String, AnyRef]]]
//            .asScala
//            .find(_.containsKey(ELASTIKNN_NAME))
//            .getOrElse(throw illArgEx(s"Couldn't find a processor with id $ELASTIKNN_NAME"))
//            .get(ELASTIKNN_NAME)
//            .asInstanceOf[util.Map[String, AnyRef]]
//          JsonFormat.fromJson[ProcessorOptions](processorOptsMap.asJson(javaMapEncoder))
//        }.recoverWith {
//          case t: Throwable => Failure(illArgEx(s"Failed to find or parse pipeline with id ${lshQueryOptions.pipelineId}", Some(t)))
//        }
//
//      val supplier = new SetOnce[KnnProcessedQueryBuilder]()
//      context.registerAsyncAction((c: Client, l: ActionListener[_]) => {
//        c.execute(
//          GetPipelineAction.INSTANCE,
//          new GetPipelineRequest(lshQueryOptions.pipelineId),
//          new ActionListener[GetPipelineResponse] {
//            override def onResponse(response: GetPipelineResponse): Unit = {
//              val procOpts = parseResponse(response).get
//              supplier.set(KnnProcessedQueryBuilder(procOpts, queryVector, lshQueryOptions.numCandidates, useCache))
//              processorOptionsCache.put(lshQueryOptions.pipelineId, procOpts)
//              l.asInstanceOf[ActionListener[Any]].onResponse(null)
//            }
//            override def onFailure(e: Exception): Unit = l.onFailure(e)
//          }
//        )
//      })
//      RewriteLater(_ => supplier.get())
//    }
//  }
//
//  /**
//    * Instantiate a [[KnnProcessedQueryBuilder]] such that the given [[ElastiKnnVector]] is only hashed once.
//    * Otherwise it would have to be hashed inside the [[KnnProcessedQueryBuilder]], which could happen on each node.
//    *
//    * @return
//    */
//  def apply(processorOptions: ProcessorOptions,
//            queryVector: ElastiKnnVector,
//            numCandidates: Int,
//            useCache: Boolean): KnnProcessedQueryBuilder = {
//    val processed = models
//      .processVector(processorOptions, queryVector)
//      .recover {
//        case t: Throwable => throw illArgEx(s"$queryVector could not be processed", Some(t))
//      }
//      .get
//    new KnnProcessedQueryBuilder(processorOptions, queryVector, processed, numCandidates, useCache)
//  }
//
//}
//
//final class KnnProcessedQueryBuilder(val processorOptions: ProcessorOptions,
//                                     val queryVector: ElastiKnnVector,
//                                     val processed: ProcessedVector,
//                                     val numCandidates: Int,
//                                     val useCache: Boolean)
//    extends AbstractQueryBuilder[KnnProcessedQueryBuilder] {
//
//  def doWriteTo(out: StreamOutput): Unit = {
//    out.writeString(processorOptions.toBase64)
//    out.writeString(queryVector.toBase64)
//    out.writeString(processed.asJson.noSpaces)
//    out.writeInt(numCandidates)
//    out.writeBoolean(useCache)
//  }
//
//  def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()
//
//  private val fieldProc: Try[String] = processorOptions.modelOptions.fieldProc match {
//    case Some(fp) => Success(fp)
//    case None     => Failure(illArgEx(s"${processorOptions.modelOptions} does not specify a processed field."))
//  }
//
//  private val similarity: Try[Similarity] = processorOptions.modelOptions.similarity match {
//    case Some(sim) => Success(sim)
//    case None      => Failure(illArgEx(s"${processorOptions.modelOptions} does not correspond to any similarity."))
//  }
//
//  /**
//    * Constructs a match query for approximate searching by hashes. Then uses that query in a [[ScriptScoreQuery]]
//    * which executes an exact query to refine the approximate results.
//    */
//  def doToQuery(context: QueryShardContext): Query = {
//
//    val query = (processorOptions.modelOptions, processed) match {
//
//      // Exact indexed jaccard uses a match query to find candidates based on intersected true indices,
//      // followed by a score function that loads the number of true indices to compute the exact similarity.
//      case (ModelOptions.ExactIndexed(exix), exixJacc: ProcessedVector.ExactIndexedJaccard) =>
//        val fieldType: MappedFieldType =
//          context.getMapperService.fullName(s"${processorOptions.fieldRaw}.ExactIndexedJaccard.numTrueIndices")
//        val fieldData: FieldData = context.getForField(fieldType)
//        ???
//
//      // Jaccard LSH uses a match query to find approximate candidates based on intersected hashes,
//      // followed by an exact score function to refine the scores for the top candidates.
//      case (ModelOptions.JaccardLsh(jacc), proc: ProcessedVector.JaccardLsh) =>
//        val mqb = new MatchQueryBuilder(jacc.fieldProcessed, proc.hashes)
//        val fieldType: MappedFieldType = context.getMapperService.fullName(processorOptions.fieldRaw)
//        val fieldData: FieldData = context.getForField(fieldType)
//        val exactScoreFunction = new KnnExactScoreFunction(SIMILARITY_JACCARD, queryVector, fieldData, useCache, Some(numCandidates))
//        new FunctionScoreQuery(mqb.toQuery(context), exactScoreFunction)
//    }
//
//    query
//  }
//
//  def doEquals(that: KnnProcessedQueryBuilder): Boolean =
//    this.processorOptions == that.processorOptions &&
//      this.queryVector == that.queryVector &&
//      this.processed == that.processed &&
//      this.useCache == that.useCache
//
//  def doHashCode(): Int = Objects.hash(processorOptions, queryVector, processed, useCache.asInstanceOf[AnyRef])
//
//  def getWriteableName: String = KnnProcessedQueryBuilder.NAME
//}
