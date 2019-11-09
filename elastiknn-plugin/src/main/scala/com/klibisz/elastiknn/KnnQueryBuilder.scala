package com.klibisz.elastiknn

import java.util.function.BiConsumer

import org.apache.lucene.search.Query
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.{AbstractQueryBuilder, QueryParser, QueryShardContext}
import io.circe.syntax._
import com.klibisz.elastiknn.utils.CirceUtils._
import org.elasticsearch.action.ActionListener
import org.elasticsearch.client.Client
import org.elasticsearch.common.lucene.search.function.ScoreFunction
import org.elasticsearch.index.query.functionscore.{ScoreFunctionBuilder, ScriptScoreFunctionBuilder}
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
    override def read(in: StreamInput): KnnQueryBuilder = {
      ???
    }
  }

}

final class KnnQueryBuilder(query: KNearestNeighborsQuery) extends AbstractQueryBuilder[KnnQueryBuilder] {

  override def doWriteTo(out: StreamOutput): Unit = {
    ???
  }

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = {
    ???
  }

  /**
    * Hits here right after Parser#fromXContent. This is supposed to return a Lucene query, and I'm not sure what happens after that.
    */
  override def doToQuery(context: QueryShardContext): Query = {
    // Will need to write the exact queries as script scoring queries.
    // https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html
    // The docs show some tempting vector functions (cosineSimilarity, l1Norm, etc.), but those are only available with X-Pack.
    // This seems to work without any special mappings.

//    val script = new Script()
//    val scoreFunction = ScriptScoreFunctionBuilder

    // If you end up needing to access other documents while executing the query, for example to retrieve the pipeline
    // that was used to ingest the document, then a similar pattern is used for geo_polygon queries against an indexed
    // shape. Look at AbstractGeometryQueryBuilder.java in the doRewrite function on line 491. It also looks like you
    // can access a client using context.registerAsyncAction().

//    context.registerAsyncAction(new BiConsumer[Client, ActionListener[_]] {
//      override def accept(client: Client, listener: ActionListener[_]): Unit = {
//        ???
//      }
//    })

    ???
  }

  override def doEquals(other: KnnQueryBuilder): Boolean = {
    ???
  }

  override def doHashCode(): Int = {
    ???
  }

  override def getWriteableName: String = {
    ???
  }
}
