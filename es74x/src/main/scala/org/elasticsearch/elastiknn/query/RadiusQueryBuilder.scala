package org.elasticsearch.elastiknn.query

import org.apache.lucene.search.Query
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.{AbstractQueryBuilder, QueryParser, QueryShardContext}

object RadiusQueryBuilder {

  val NAME: String = "elastiknn_radius"

  object Reader extends Writeable.Reader[Builder] {
    override def read(in: StreamInput): Builder = ???
  }

  object Parser extends QueryParser[Builder] {
    override def fromXContent(parser: XContentParser): Builder = ???
  }

  class Builder extends AbstractQueryBuilder[Builder] {
    override def doWriteTo(out: StreamOutput): Unit = ???

    override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ???

    override def doToQuery(context: QueryShardContext): Query = {
      // Can probably implement this using a script filter query:
      // https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-query.html

      ???
    }

    override def doEquals(other: Builder): Boolean = ???

    override def doHashCode(): Int = ???

    override def getWriteableName: String = ???
  }

}
