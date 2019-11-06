package com.klibisz.elastiknn

import org.apache.lucene.search.Query
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.{QueryBuilder, QueryParser, QueryShardContext}

object RadiusQuery {

  val NAME: String = "elastiknn_radius"

  object Reader extends Writeable.Reader[Builder] {
    override def read(in: StreamInput): Builder = ???
  }

  object Parser extends QueryParser[Builder] {
    override def fromXContent(parser: XContentParser): Builder = ???
  }

  class Builder extends QueryBuilder {
    override def toQuery(context: QueryShardContext): Query = ???

    override def queryName(queryName: String): QueryBuilder = ???

    override def queryName(): String = ???

    override def boost(): Float = ???

    override def boost(boost: Float): QueryBuilder = ???

    override def getName: String = ???

    override def getWriteableName: String = ???

    override def writeTo(out: StreamOutput): Unit = ???

    override def toXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder = ???
  }

}
