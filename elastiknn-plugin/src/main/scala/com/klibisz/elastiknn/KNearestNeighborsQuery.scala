package com.klibisz.elastiknn

import org.apache.lucene.search.Query
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.{AbstractQueryBuilder, QueryBuilder, QueryParser, QueryShardContext}

object KNearestNeighborsQuery {

  val NAME = "elastiknn_knn"

  object Parser extends QueryParser[Builder] {

    /** This is the first method that gets hit when you run this query. */
    override def fromXContent(parser: XContentParser): Builder = {
      // TODO: use parser.text() or parser.map() to get the content
      new Builder()
    }

  }

  object Reader extends Writeable.Reader[Builder] {
    override def read(in: StreamInput): Builder = {
      ???
    }
  }

  final class Builder() extends AbstractQueryBuilder[Builder] {
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
      ???
    }

    override def doEquals(other: Builder): Boolean = {
      ???
    }

    override def doHashCode(): Int = {
      ???
    }

    override def getWriteableName: String = {
      ???
    }
  }

}
