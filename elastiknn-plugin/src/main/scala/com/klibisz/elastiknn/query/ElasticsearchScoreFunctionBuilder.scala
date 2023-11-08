package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.ElastiknnException.ElastiknnUnsupportedOperationException
import com.klibisz.elastiknn.api.{NearestNeighborsQuery, Vec}
import org.elasticsearch.TransportVersion
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.lucene.search.function.ScoreFunction
import org.elasticsearch.index.query.SearchExecutionContext
import org.elasticsearch.index.query.functionscore.{ScoreFunctionBuilder, ScoreFunctionParser}
import org.elasticsearch.xcontent.{ToXContent, XContentBuilder, XContentParser}

import java.util.Objects

final class ElasticsearchScoreFunctionBuilder(
    val query: NearestNeighborsQuery,
    val weight: Float,
    elastiknnQueryBuilder: ElastiknnQueryBuilder
) extends ScoreFunctionBuilder[ElasticsearchScoreFunctionBuilder] {

  setWeight(weight)

  override def doWriteTo(out: StreamOutput): Unit =
    out.writeString(ElasticsearchQueryBuilder.encodeB64(query))

  override def getName: String = ElasticsearchScoreFunctionBuilder.NAME

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doEquals(other: ElasticsearchScoreFunctionBuilder): Boolean = other.query == query && other.weight == weight

  override def doHashCode(): Int = Objects.hash(query, weight.asInstanceOf[java.lang.Float])

  override def doToFunction(context: SearchExecutionContext): ScoreFunction =
    elastiknnQueryBuilder.build(query, context).map(_.toScoreFunction(context.getIndexReader)).get

  override def getMinimalSupportedVersion: TransportVersion = TransportVersion.current()
}

object ElasticsearchScoreFunctionBuilder {

  val NAME: String = s"${ELASTIKNN_NAME}_nearest_neighbors"

  final class Reader(elastiknnQueryBuilder: ElastiknnQueryBuilder) extends Writeable.Reader[ElasticsearchScoreFunctionBuilder] {
    override def read(in: StreamInput): ElasticsearchScoreFunctionBuilder = {
      val weight = in.readOptionalFloat()
      val s = in.readString()
      val query = ElasticsearchQueryBuilder.decodeB64[NearestNeighborsQuery](s)
      new ElasticsearchScoreFunctionBuilder(query, weight, elastiknnQueryBuilder)
    }
  }

  final class Parser(elastiknnQueryBuilder: ElastiknnQueryBuilder) extends ScoreFunctionParser[ElasticsearchScoreFunctionBuilder] {
    private val elasticsearchQueryBuilderParser = new ElasticsearchQueryBuilder.Parser(elastiknnQueryBuilder)
    override def fromXContent(parser: XContentParser): ElasticsearchScoreFunctionBuilder = {
      val knnqb = elasticsearchQueryBuilderParser.fromXContent(parser)
      knnqb.query.vec match {
        case _: Vec.Indexed =>
          val msg = "The score function does not support indexed vectors. Provide a literal vector instead."
          throw new ElastiknnUnsupportedOperationException(msg)
        case _ => new ElasticsearchScoreFunctionBuilder(knnqb.query, 1f, elastiknnQueryBuilder)
      }
    }
  }
}
