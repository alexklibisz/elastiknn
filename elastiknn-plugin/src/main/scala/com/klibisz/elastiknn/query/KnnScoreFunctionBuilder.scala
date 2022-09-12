package com.klibisz.elastiknn.query

import java.util.Objects
import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.ElastiknnException.ElastiknnUnsupportedOperationException
import com.klibisz.elastiknn.api.{NearestNeighborsQuery, Vec}
import org.elasticsearch.Version
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.lucene.search.function.ScoreFunction
import org.elasticsearch.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.SearchExecutionContext
import org.elasticsearch.index.query.functionscore.{ScoreFunctionBuilder, ScoreFunctionParser}

final class KnnScoreFunctionBuilder(val query: NearestNeighborsQuery, val weight: Float)
    extends ScoreFunctionBuilder[KnnScoreFunctionBuilder] {

  setWeight(weight)

  override def doWriteTo(out: StreamOutput): Unit =
    out.writeString(KnnQueryBuilder.encodeB64(query))

  override def getName: String = KnnScoreFunctionBuilder.NAME

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doEquals(other: KnnScoreFunctionBuilder): Boolean = other.query == query && other.weight == weight

  override def doHashCode(): Int = Objects.hash(query, weight.asInstanceOf[java.lang.Float])

  override def doToFunction(context: SearchExecutionContext): ScoreFunction = {
    ElastiknnQuery(query, context).map(_.toScoreFunction(context.getIndexReader)).get
  }

  override def getMinimalSupportedVersion: Version = Version.V_EMPTY
}

object KnnScoreFunctionBuilder {

  val NAME: String = s"${ELASTIKNN_NAME}_nearest_neighbors"

  object Reader extends Writeable.Reader[KnnScoreFunctionBuilder] {
    override def read(in: StreamInput): KnnScoreFunctionBuilder = {
      val weight = in.readOptionalFloat()
      val s = in.readString()
      val query = KnnQueryBuilder.decodeB64[NearestNeighborsQuery](s)
      new KnnScoreFunctionBuilder(query, weight)
    }
  }

  object Parser extends ScoreFunctionParser[KnnScoreFunctionBuilder] {
    override def fromXContent(parser: XContentParser): KnnScoreFunctionBuilder = {
      val knnqb = KnnQueryBuilder.Parser.fromXContent(parser)
      knnqb.query.vec match {
        case _: Vec.Indexed =>
          val msg = "The score function does not support indexed vectors. Provide a literal vector instead."
          throw new ElastiknnUnsupportedOperationException(msg)
        case _ => new KnnScoreFunctionBuilder(knnqb.query, 1f)
      }
    }
  }
}
