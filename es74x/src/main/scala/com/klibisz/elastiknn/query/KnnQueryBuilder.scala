package com.klibisz.elastiknn.query

import java.util
import java.util.Objects
import java.util.concurrent.Callable

import com.google.common.cache.CacheBuilder
import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, IndexedQueryVector, LshQueryOptions, QueryOptions, QueryVector}
import com.klibisz.elastiknn.utils.CirceUtils._
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.processor.StoredScripts
import io.circe.syntax._
import org.apache.logging.log4j.{LogManager, Logger}
import org.apache.lucene.search.Query
import org.elasticsearch.action.ingest.{GetPipelineAction, GetPipelineRequest}
import org.elasticsearch.client.Client
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.lucene.search.function.{ScriptScoreFunction, ScriptScoreQuery}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.functionscore.ScriptScoreFunctionBuilder
import org.elasticsearch.index.query.{AbstractQueryBuilder, ExistsQueryBuilder, QueryParser, QueryShardContext}
import org.elasticsearch.script.Script
import scalapb_circe.JsonFormat

import scala.collection.JavaConverters._

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
    override def read(in: StreamInput): KnnQueryBuilder = ???
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

  import KnnQueryBuilder.processorOptions

  private val logger: Logger = LogManager.getLogger(getClass)

  // Use the query options to build a lucene query.
  override def doToQuery(context: QueryShardContext): Query = query.queryOptions match {
    case QueryOptions.Exact(exactOpts) => exactQuery(context, exactOpts)
    case QueryOptions.Lsh(lshOpts)     => lshQuery(context, lshOpts)
    case QueryOptions.Empty            => throw new IllegalArgumentException("Must provide query options")
  }

  private def getIndexedQueryVector(indexedQueryVector: IndexedQueryVector): Array[Double] = ???

  // Exact queries get converted to script-score queries.
  private def exactQuery(context: QueryShardContext, exactOpts: ExactQueryOptions): Query = {
    val procOpts = processorOptions(SharedClient.client, query.pipelineId, query.processorId)
    val b: Array[Double] = query.queryVector match {
      case QueryVector.Given(givenQueryVector)     => givenQueryVector.vector
      case QueryVector.Indexed(indexedQueryVector) => getIndexedQueryVector(indexedQueryVector)
      case QueryVector.Empty                       => throw new IllegalArgumentException("Must provide query vector")
    }
    val script: Script = exactOpts.distance match {
      case Distance.DISTANCE_ANGULAR => StoredScripts.exactAngular.script(procOpts.fieldProcessed, b)
      case _                         => ???
    }

    val existsQuery = new ExistsQueryBuilder(procOpts.fieldProcessed).toQuery(context)
    val function = new ScriptScoreFunctionBuilder(script).toFunction(context)
    new ScriptScoreQuery(existsQuery, function.asInstanceOf[ScriptScoreFunction], 0.0f)
  }

  private def lshQuery(context: QueryShardContext, lshOpts: LshQueryOptions): Query = ???

  // TODO: what is this used for?
  override def doWriteTo(out: StreamOutput): Unit = ()

  // TODO: This function seems to only get called when there is an error.
  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doEquals(other: KnnQueryBuilder): Boolean = this.query == other.query

  override def doHashCode(): Int = Objects.hash(this.query)

  override def getWriteableName: String = KnnQueryBuilder.NAME
}
