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
    // https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html
    // For exact queries, some of the distance functions are already implemented:
    // https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html#vector-functions
    // Look for cosineSimilarity(), l1norm(), and l2norm().
    // If you end up needing to access other documents while executing the query, for example to retrieve the pipeline
    // that was used to ingest the document, then a similar pattern is used for geo_polygon queries against an indexed
    // shape. Look at AbstractGeometryQueryBuilder.java in the doRewrite function on line 491. It also looks like you
    // can access a client using context.registerAsyncAction().

//    context.registerAsyncAction(new BiConsumer[Client, ActionListener[_]] {
//      override def accept(client: Client, listener: ActionListener[_]): Unit = {
//
//        ???
//      }
//    })

    println(this.query)

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
