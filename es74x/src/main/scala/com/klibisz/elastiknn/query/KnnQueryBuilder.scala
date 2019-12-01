package com.klibisz.elastiknn.query

import java.util
import java.util.Objects
import java.util.concurrent.Callable
import java.util.function.BiConsumer

import com.google.common.cache.CacheBuilder
import com.google.common.io.BaseEncoding
import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn.KNearestNeighborsQuery.{
  ExactQueryOptions,
  IndexedQueryVector,
  LshQueryOptions,
  QueryOptions,
  QueryVector
}
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.processor.StoredScripts
import com.klibisz.elastiknn.utils.CirceUtils._
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
import org.elasticsearch.common.lucene.search.function.{
  ScriptScoreFunction,
  ScriptScoreQuery
}
import org.elasticsearch.common.xcontent.{
  ToXContent,
  XContentBuilder,
  XContentParser
}
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
    override def read(in: StreamInput): KnnQueryBuilder =
      new KnnQueryBuilder(in)
  }

  // TODO: move this out to a config file.
  private[this] val getProcessorOptionsTimeout: Long = 1000

  // Keep an internal cache of the processor options.
  private[this] val processorOptionsCache = CacheBuilder.newBuilder.softValues
    .build[(String, String), ProcessorOptions]()

  // Method with logic for fetching, parsing, and caching processor options.
  // It uses the client in a blocking fashion, but there should be very few pipelines compared to the number of vectors.
  def processorOptions(client: Client,
                       pipelineId: String,
                       processorId: String): ProcessorOptions = {
    lazy val callable: Callable[ProcessorOptions] = () =>
      KnnQueryBuilder.synchronized {
        val getRes = client
          .execute(new GetPipelineAction(), new GetPipelineRequest(pipelineId))
          .actionGet(getProcessorOptionsTimeout)
        require(getRes.pipelines.size() > 0,
                s"Found no pipelines with id $pipelineId")
        val configMap = getRes.pipelines.get(0).getConfigAsMap
        require(configMap.containsKey("processors"),
                s"Pipeline $pipelineId has no processors")
        val procsList = configMap
          .get("processors")
          .asInstanceOf[util.ArrayList[
            util.Map[String, util.Map[String, Object]]]]
        val procOptsOpt = procsList.asScala
          .find(_.containsKey(processorId))
          .map(_.get(processorId))
        require(
          procOptsOpt.isDefined,
          s"Found no processor with id $processorId for pipeline $pipelineId")
        val procOptsJson = procOptsOpt.get.asJson
        val procOpts = JsonFormat.fromJson[ProcessorOptions](procOptsJson)
        procOpts
    }
    processorOptionsCache.get((pipelineId, processorId), callable)
  }

}

final class KnnQueryBuilder(val query: KNearestNeighborsQuery)
    extends AbstractQueryBuilder[KnnQueryBuilder] {

  /** Decodes a KnnQueryBuilder from the StreamInput as a base64 string. Using the ByteArray directly doesn't work. */
  def this(in: StreamInput) =
    this({
      // https://github.com/elastic/elasticsearch/blob/master/server/src/main/java/org/elasticsearch/index/query/AbstractQueryBuilder.java#L66-L68
      in.readFloat()
      in.readOptionalString()
      val ba = BaseEncoding.base64.decode(in.readString())
      KNearestNeighborsQuery.parseFrom(ba)
    })

  /** Encodes the KnnQueryBuilder to a StreamOutput as a base64 string. */
  override def doWriteTo(out: StreamOutput): Unit =
    out.writeString(BaseEncoding.base64.encode(query.toByteArray))

  // Use the query options to build a lucene query.
  override def doToQuery(context: QueryShardContext): Query =
    (query.queryOptions, query.queryVector) match {
      case (QueryOptions.Exact(opts), QueryVector.Given(query)) =>
        exactGivenQuery(context, opts, query).get
      case (QueryOptions.Lsh(opts), QueryVector.Given(query)) =>
        lshGivenQuery(context, opts, query).get
      case (QueryOptions.Empty, QueryVector.Empty) =>
        throw new IllegalArgumentException(
          "Missing query options _and_ query vector")
      case (_, QueryVector.Indexed(_)) =>
        throw new IllegalArgumentException(
          "Indexed vector query should should have been rewritten")
      case (QueryOptions.Empty, _) =>
        throw new IllegalArgumentException("Missing query options")
      case (_, QueryVector.Empty) =>
        throw new IllegalArgumentException("Missing query vector")
    }

  /** Checks if a query needs to be rewritten and rewrites it. */
  override def doRewrite(context: QueryRewriteContext): QueryBuilder = {
    query.queryVector match {
      case QueryVector.Given(_)    => this
      case QueryVector.Indexed(qv) => rewriteIndexedQuery(context, qv)
      case QueryVector.Empty =>
        throw new IllegalStateException("Query must be provided")
    }
  }

  private def rewriteIndexedQuery(context: QueryRewriteContext,
                                  qv: IndexedQueryVector): QueryBuilder = {
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

  /** Returns a Try of an exact query using a given query vector. */
  private def exactGivenQuery(context: QueryShardContext,
                              opts: ExactQueryOptions,
                              ekv: ElastiKnnVector): Try[Query] = {
    import ElastiKnnVector.Vector._
    (opts.similarity, ekv.vector) match {
      case (SIMILARITY_ANGULAR, dvec: DoubleVector) =>
        Success(
          scriptScoreQuery(
            context,
            opts.fieldRaw,
            StoredScripts.exactAngular.script(opts.fieldRaw, dvec)))
      case (SIMILARITY_L1, dvec: DoubleVector) =>
        Success(
          scriptScoreQuery(context,
                           opts.fieldRaw,
                           StoredScripts.exactL1.script(opts.fieldRaw, dvec)))
      case (SIMILARITY_L2, dvec: DoubleVector) =>
        Success(
          scriptScoreQuery(context,
                           opts.fieldRaw,
                           StoredScripts.exactL2.script(opts.fieldRaw, dvec)))
      case (SIMILARITY_HAMMING, bvec: BoolVector) =>
        Success(
          scriptScoreQuery(
            context,
            opts.fieldRaw,
            StoredScripts.exactHamming.script(opts.fieldRaw, bvec)))
      case (SIMILARITY_JACCARD, bvec: BoolVector) =>
        Success(
          scriptScoreQuery(
            context,
            opts.fieldRaw,
            StoredScripts.exactJaccard.script(opts.fieldRaw, bvec)))
      case (_, Empty) =>
        Failure(new IllegalArgumentException("Must provide vector"))
      case (_, _) =>
        Failure(IncompatibleDistanceAndVectorException(opts.similarity, ekv))
    }
  }

  private def scriptScoreQuery(context: QueryShardContext,
                               field: String,
                               script: Script): ScriptScoreQuery = {
    val exists = new ExistsQueryBuilder(field).toQuery(context)
    val function = new ScriptScoreFunctionBuilder(script).toFunction(context)
    new ScriptScoreQuery(exists,
                         function.asInstanceOf[ScriptScoreFunction],
                         0.0f)
  }

  private def lshGivenQuery(context: QueryShardContext,
                            lshOpts: LshQueryOptions,
                            ekv: ElastiKnnVector): Try[Query] = ???

  // TODO: This function seems to only get called when there is an error.
  override def doXContent(builder: XContentBuilder,
                          params: ToXContent.Params): Unit = ()

  override def doEquals(other: KnnQueryBuilder): Boolean =
    this.query == other.query

  override def doHashCode(): Int = Objects.hash(this.query)

  override def getWriteableName: String = KnnQueryBuilder.NAME

}
