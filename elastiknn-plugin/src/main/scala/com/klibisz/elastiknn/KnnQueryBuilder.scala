package com.klibisz.elastiknn

import org.apache.lucene.search.Query
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.{AbstractQueryBuilder, QueryParser, QueryShardContext}

object KnnQueryBuilder {

  val NAME = "elastiknn_knn"

  object Parser extends QueryParser[KnnQueryBuilder] {

    /** This is the first method that gets hit when you run this query. */
    override def fromXContent(parser: XContentParser): KnnQueryBuilder = {
      // TODO: use parser.text() or parser.map() to get the content
      new KnnQueryBuilder()
    }

  }

  object Reader extends Writeable.Reader[KnnQueryBuilder] {
    override def read(in: StreamInput): KnnQueryBuilder = {
      ???
    }
  }

}

final class KnnQueryBuilder() extends AbstractQueryBuilder[KnnQueryBuilder] {
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
