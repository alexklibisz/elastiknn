package com.klibisz.elastiknn.query

import java.util.Objects

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.ElastiknnException.ElastiknnIllegalArgumentException
import com.klibisz.elastiknn.api.Vec
import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.search.{Explanation, Query, ScoreMode, Weight}
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.lucene.search.function
import org.elasticsearch.common.lucene.search.function.{CombineFunction, LeafScoreFunction, ScoreFunction}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.QueryShardContext
import org.elasticsearch.index.query.functionscore.{ScoreFunctionBuilder, ScoreFunctionParser}

import scala.util.{Failure, Success, Try}

object KnnScoreFunctionBuilder {

  val NAME: String = s"${ELASTIKNN_NAME}_nearest_neighbors"

  object Reader extends Writeable.Reader[KnnScoreFunctionBuilder] {
    override def read(in: StreamInput): KnnScoreFunctionBuilder = {
      val knnQueryBuilder = KnnQueryBuilder.Reader.read(in)
      new KnnScoreFunctionBuilder(knnQueryBuilder)
    }
  }

  object Parser extends ScoreFunctionParser[KnnScoreFunctionBuilder] {
    override def fromXContent(parser: XContentParser): KnnScoreFunctionBuilder = {
      val knnQueryBuilder = KnnQueryBuilder.Parser.fromXContent(parser)
      new KnnScoreFunctionBuilder(knnQueryBuilder)
    }
  }

  class ScoreFunction private (val weight: Weight) extends function.ScoreFunction(CombineFunction.REPLACE) {

    override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
      val scorer = weight.scorer(ctx)
      val iterator = scorer.iterator()
      new LeafScoreFunction {
        override def score(docId: Int, subQueryScore: Float): Double = {
          iterator.advance(docId)
          scorer.score()
        }
        override def explainScore(docId: Int, subQueryScore: Explanation): Explanation = {
          Explanation.`match`(
            score(docId, subQueryScore.getValue.floatValue()).toFloat,
            s"$NAME score function"
          )
        }
      }
    }

    override def needsScores(): Boolean = false

    override def doEquals(other: function.ScoreFunction): Boolean = other match {
      case f: KnnScoreFunctionBuilder.ScoreFunction => weight == f.weight
      case _                                        => false
    }

    override def doHashCode(): Int = Objects.hash(weight)
  }

  object ScoreFunction {
    def apply(knnQueryBuilder: KnnQueryBuilder, context: QueryShardContext): Try[ScoreFunction] = {
      knnQueryBuilder.query.vec match {
        case _: Vec.Indexed =>
          Failure(new ElastiknnIllegalArgumentException(s"Score functions with indexed vectors are not yet supported"))
        case _ =>
          val query: Query = knnQueryBuilder.doToQuery(context)
          val weight = query.createWeight(context.searcher(), ScoreMode.TOP_SCORES, 1f)
          Success(new ScoreFunction(weight))
      }
    }
  }

}

final case class KnnScoreFunctionBuilder(knnQueryBuilder: KnnQueryBuilder) extends ScoreFunctionBuilder[KnnScoreFunctionBuilder] {
  override def doWriteTo(out: StreamOutput): Unit = {
    out.writeString(KnnQueryBuilder.encodeB64(knnQueryBuilder.query))
  }

  override def getName: String = KnnScoreFunctionBuilder.NAME

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = {
    ()
  }

  override def doEquals(other: KnnScoreFunctionBuilder): Boolean =
    other.knnQueryBuilder.query == knnQueryBuilder.query

  override def doHashCode(): Int = Objects.hash(knnQueryBuilder)

  override def doToFunction(context: QueryShardContext): ScoreFunction =
    KnnScoreFunctionBuilder.ScoreFunction(knnQueryBuilder, context).get
}
