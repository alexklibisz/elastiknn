package com.klibisz.elastiknn.query

import java.util
import java.util.Objects
import java.util.concurrent.Callable

import com.google.common.cache.CacheBuilder
import com.klibisz.elastiknn.Distance._
import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, LshQueryOptions, QueryOptions, QueryVector}
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.processor.StoredScripts
import com.klibisz.elastiknn.utils.CirceUtils._
import io.circe.syntax._
import org.apache.logging.log4j.{LogManager, Logger}
import org.apache.lucene.search.Query
import org.elasticsearch.action.ingest.{GetPipelineAction, GetPipelineRequest}
import org.elasticsearch.client.Client
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

  val NAME = "elastiknn_knn"

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
      // Based on this: https://github.com/elastic/elasticsearch/blob/master/server/src/main/java/org/elasticsearch/index/query/AbstractQueryBuilder.java#L66-L68
      in.readFloat()
      in.readOptionalString()
      new KnnQueryBuilder(JsonFormat.fromJsonString[KNearestNeighborsQuery](in.readString()))
    }
  }

  // TODO: move this out to a config file.
  private[this] val getProcessorOptionsTimeout: Long = 1000

  // Keep an internal cache of the processor options.
  private[this] val processorOptionsCache = CacheBuilder.newBuilder.softValues.build[(String, String), ProcessorOptions]()

  // Method with logic for fetching, parsing, and caching processor options.
  // It uses the client in a blocking fashion, but there should be very few pipelines compared to the number of vectors.
  def processorOptions(client: Client, pipelineId: String, processorId: String): ProcessorOptions = {
    lazy val callable: Callable[ProcessorOptions] = () =>
      KnnQueryBuilder.synchronized {
        val getRes = client.execute(new GetPipelineAction(), new GetPipelineRequest(pipelineId)).actionGet(getProcessorOptionsTimeout)
        require(getRes.pipelines.size() > 0, s"Found no pipelines with id $pipelineId")
        val configMap = getRes.pipelines.get(0).getConfigAsMap
        require(configMap.containsKey("processors"), s"Pipeline $pipelineId has no processors")
        val procsList = configMap.get("processors").asInstanceOf[util.ArrayList[util.Map[String, util.Map[String, Object]]]]
        val procOptsOpt = procsList.asScala.find(_.containsKey(processorId)).map(_.get(processorId))
        require(procOptsOpt.isDefined, s"Found no processor with id $processorId for pipeline $pipelineId")
        val procOptsJson = procOptsOpt.get.asJson
        val procOpts = JsonFormat.fromJson[ProcessorOptions](procOptsJson)
        procOpts
    }
    processorOptionsCache.get((pipelineId, processorId), callable)
  }

}

final class KnnQueryBuilder(val query: KNearestNeighborsQuery) extends AbstractQueryBuilder[KnnQueryBuilder] {

  private val logger: Logger = LogManager.getLogger(getClass)

  // NOTES:
  // you can get a client from a QueryShardContext: context.getClient
  // you can get a threadpool from the client, and an execution context from the threadpool:
  // val executionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(context.getClient.threadPool.executor(ThreadPool.Names.SEARCH))

  // Use the query options to build a lucene query.
  override def doToQuery(context: QueryShardContext): Query = (query.queryOptions, query.queryVector) match {
    case (QueryOptions.Exact(opts), QueryVector.Given(query)) => exactGivenQuery(context, opts, query).get
    case (QueryOptions.Lsh(opts), QueryVector.Given(query)) => lshGivenQuery(context, opts, query).get
    case _ => ???
  }

  override def doRewrite(queryShardContext: QueryRewriteContext): QueryBuilder = {
    // TODO: I think this is where I should fetch the indexed vectors and processors if needed. This seems to be how
    // elasticsearch's GeoQueryBuilder solves this problem. In this case I think it should rewrite an indexed vector query
    // into a given vector query.
    super.doRewrite(queryShardContext)
  }

  /** Returns a Try of an exact query using a given query vector. */
  private def exactGivenQuery(context: QueryShardContext, opts: ExactQueryOptions, ekv: ElastiKnnVector): Try[Query] = {
    import ElastiKnnVector.Vector._
    (opts.distance, ekv.vector) match {
      case (DISTANCE_ANGULAR, dvec: DoubleVector) =>
        Success(scriptScoreQuery(context, opts.fieldRaw, StoredScripts.exactAngular.script(opts.fieldRaw, dvec)))
      case (DISTANCE_L1,  dvec: DoubleVector) =>
        Success(scriptScoreQuery(context, opts.fieldRaw, StoredScripts.exactL1.script(opts.fieldRaw, dvec)))
      case (DISTANCE_L2, dvec: DoubleVector) =>
        Success(scriptScoreQuery(context, opts.fieldRaw, StoredScripts.exactL2.script(opts.fieldRaw, dvec)))
      case (DISTANCE_HAMMING, bvec: BoolVector) =>
        Success(scriptScoreQuery(context, opts.fieldRaw, StoredScripts.exactHamming.script(opts.fieldRaw, bvec)))
      case (DISTANCE_JACCARD, bvec: BoolVector) =>
        Success(scriptScoreQuery(context, opts.fieldRaw, StoredScripts.exactJaccard.script(opts.fieldRaw, bvec)))
      case (_, Empty) => Failure(new IllegalArgumentException("Must provide vector"))
      case (_, _) => Failure(IncompatibleDistanceAndVectorException(opts.distance, ekv))
    }
  }

  private def scriptScoreQuery(context: QueryShardContext, field: String, script: Script): ScriptScoreQuery = {
    val exists = new ExistsQueryBuilder(field).toQuery(context)
    val function = new ScriptScoreFunctionBuilder(script).toFunction(context)
    new ScriptScoreQuery(exists, function.asInstanceOf[ScriptScoreFunction], 0.0f)
  }

  private def lshGivenQuery(context: QueryShardContext, lshOpts: LshQueryOptions, ekv: ElastiKnnVector): Try[Query] = ???

  // TODO: what is this used for?
  override def doWriteTo(out: StreamOutput): Unit = {
    out.writeString(JsonFormat.toJsonString(query))
  }

  // TODO: This function seems to only get called when there is an error.
  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doEquals(other: KnnQueryBuilder): Boolean = this.query == other.query

  override def doHashCode(): Int = Objects.hash(this.query)

  override def getWriteableName: String = KnnQueryBuilder.NAME


}
