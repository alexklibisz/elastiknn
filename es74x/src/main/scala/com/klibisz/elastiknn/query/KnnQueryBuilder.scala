package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.ELASTIKNN_NAME
import org.apache.lucene.search.Query
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.{AbstractQueryBuilder, QueryParser, QueryShardContext}

object KnnQueryBuilder {

  val NAME: String = s"${ELASTIKNN_NAME}_nearest_neighbors"

  object Parser extends QueryParser[KnnQueryBuilder] {
    override def fromXContent(parser: XContentParser): KnnQueryBuilder = {
      ???
    }
  }

  object Reader extends Writeable.Reader[KnnQueryBuilder] {
    override def read(in: StreamInput): KnnQueryBuilder = {
      // https://github.com/elastic/elasticsearch/blob/master/server/src/main/java/org/elasticsearch/index/query/AbstractQueryBuilder.java#L66-L68
      in.readFloat()
      in.readOptionalString()

      ???
    }
  }

}

final class KnnQueryBuilder() extends AbstractQueryBuilder[KnnQueryBuilder] {
  override def doWriteTo(out: StreamOutput): Unit = ???

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ???

  override def doToQuery(context: QueryShardContext): Query = ???

  override def doEquals(other: KnnQueryBuilder): Boolean = ???

  override def doHashCode(): Int = ???

  override def getWriteableName: String = ???
}
