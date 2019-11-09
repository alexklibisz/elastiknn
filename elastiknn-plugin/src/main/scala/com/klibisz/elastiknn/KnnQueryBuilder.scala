package com.klibisz.elastiknn

import java.util.Objects

import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, IndexedQueryVector, LshQueryOptions, QueryOptions, QueryVector}
import com.klibisz.elastiknn.utils.CirceUtils._
import io.circe.syntax._
import org.apache.lucene.search.Query
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.functionscore.{ScriptScoreFunctionBuilder, ScriptScoreQueryBuilder}
import org.elasticsearch.index.query.{AbstractQueryBuilder, MatchAllQueryBuilder, QueryParser, QueryShardContext}
import org.elasticsearch.script.Script
import scalapb_circe.JsonFormat

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

}

final class KnnQueryBuilder(val query: KNearestNeighborsQuery) extends AbstractQueryBuilder[KnnQueryBuilder] {

  // Use the query options to build a lucene query.
  override def doToQuery(context: QueryShardContext): Query = query.queryOptions match {
    case QueryOptions.Exact(exactOpts) => exactQuery(context, exactOpts)
    case QueryOptions.Lsh(lshOpts)     => lshQuery(context, lshOpts)
    case QueryOptions.Empty            => throw new IllegalArgumentException("Must provide query options")
  }

  // If you end up needing to access other documents while executing the query, for example to retrieve the pipeline
  // that was used to ingest the document, then a similar pattern is used for geo_polygon queries against an indexed
  // shape. Look at AbstractGeometryQueryBuilder.java in the doRewrite function on line 491. It also looks like you
  // can access a client using context.registerAsyncAction().

  //    context.registerAsyncAction(new BiConsumer[Client, ActionListener[_]] {
  //      override def accept(client: Client, listener: ActionListener[_]): Unit = {
  //        ???
  //      }
  //    })

  // TODO: actually load this from the cluster.
  private val processorOptions = ProcessorOptions(
    fieldProcessed = "vec_proc"
  )

  private def getIndexedQueryVector(indexedQueryVector: IndexedQueryVector): Array[Double] = ???

  // Exact queries get converted to script-score queries.
  private def exactQuery(context: QueryShardContext, exactOpts: ExactQueryOptions): Query = {
    val b: Array[Double] = query.queryVector match {
      case QueryVector.Given(givenQueryVector)     => givenQueryVector.vector
      case QueryVector.Indexed(indexedQueryVector) => getIndexedQueryVector(indexedQueryVector)
      case QueryVector.Empty                       => throw new IllegalArgumentException("Must provide query vector")
    }
    val script: Script = exactOpts.distance match {
      case Distance.DISTANCE_ANGULAR => StoredScripts.exactAngular.script(processorOptions.fieldProcessed, b)
      case _                         => ???
    }
    // TODO: use an exists query to filter for fields which contain the processorOptions.fieldProcessed field.
    new ScriptScoreQueryBuilder(new MatchAllQueryBuilder(), new ScriptScoreFunctionBuilder(script)).toQuery(context)
  }

  private def lshQuery(context: QueryShardContext, lshOpts: LshQueryOptions): Query = ???

  // TODO: what is this used for?
  override def doWriteTo(out: StreamOutput): Unit = ???

  // TODO: what is this used for? I think this function gets called when there's an error.
  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ???

  override def doEquals(other: KnnQueryBuilder): Boolean = this.query == other.query

  override def doHashCode(): Int = Objects.hash(this.query)

  override def getWriteableName: String = KnnQueryBuilder.NAME
}
