package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.ELASTIKNN_NAME
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.lucene.search.function.ScoreFunction
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.QueryShardContext
import org.elasticsearch.index.query.functionscore.{ScoreFunctionBuilder, ScoreFunctionParser}

object KnnScoreFunctionBuilder {

  val NAME: String = s"${ELASTIKNN_NAME}_nearest_neighbors"

  object Reader extends Writeable.Reader[KnnScoreFunctionBuilder] {
    override def read(in: StreamInput): KnnScoreFunctionBuilder = {
      ???
    }
  }

  object Parser extends ScoreFunctionParser[KnnScoreFunctionBuilder] {
    override def fromXContent(parser: XContentParser): KnnScoreFunctionBuilder = {
      ???
    }
  }

}

final case class KnnScoreFunctionBuilder() extends ScoreFunctionBuilder[KnnScoreFunctionBuilder] {
  override def doWriteTo(out: StreamOutput): Unit = ???

  override def getName: String = ???

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ???

  override def doEquals(functionBuilder: KnnScoreFunctionBuilder): Boolean = ???

  override def doHashCode(): Int = ???

  override def doToFunction(context: QueryShardContext): ScoreFunction = ???
}
