package com.klibisz.elastiknn.query

import java.util.Objects

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api.NearestNeighborsQuery
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.lucene.search.function.ScoreFunction
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query.QueryShardContext
import org.elasticsearch.index.query.functionscore.{ScoreFunctionBuilder, ScoreFunctionParser}

final case class KnnScoreFunctionBuilder(query: NearestNeighborsQuery, weight: Float)
    extends ScoreFunctionBuilder[KnnScoreFunctionBuilder] {

  this.setWeight(weight)

  override def doWriteTo(out: StreamOutput): Unit =
    out.writeString(KnnQueryBuilder.encodeB64(query))

  override def getName: String = KnnScoreFunctionBuilder.NAME

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doEquals(other: KnnScoreFunctionBuilder): Boolean = other.query == query

  override def doHashCode(): Int = Objects.hash(query)

  override def doToFunction(context: QueryShardContext): ScoreFunction = {
    ElastiknnQuery(query, context).map(_.toScoreFunction(context)).get
  }
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
      new KnnScoreFunctionBuilder(knnqb.query, 1f)
    }
  }

//  class ScoreFunction private (val query: NearestNeighborsQuery) extends function.ScoreFunction(CombineFunction.REPLACE) {
//
//    override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
//
//      query match {
//        case NearestNeighborsQuery.Exact(field, similarity, vec) =>
//          ???
//          similarity match {
//            case Similarity.Jaccard => ???
//            case Similarity.Hamming => ???
//            case Similarity.L1      => ???
//            case Similarity.L2      => ???
//            case Similarity.Angular => ???
//          }
//
//        case NearestNeighborsQuery.SparseIndexed(field, similarity, vec) => ???
//        case query: NearestNeighborsQuery.ApproximateQuery               => ???
//      }
////      new LeafScoreFunction {
////        override def score(docId: Int, subQueryScore: Float): Double = ???
////
////        override def explainScore(docId: Int, subQueryScore: Explanation): Explanation = ???
////      }
////
////      val scorer = weight.scorer(ctx)
////      val iterator = scorer.iterator()
////      new LeafScoreFunction {
////        override def score(docId: Int, subQueryScore: Float): Double = {
////          iterator.advance(docId)
////          val s = scorer.score()
////          println(s"Score for ${docId} is $s")
////          s
////        }
////        override def explainScore(docId: Int, subQueryScore: Explanation): Explanation = {
////          Explanation.`match`(
////            score(docId, subQueryScore.getValue.floatValue()).toFloat,
////            s"$NAME score function"
////          )
////        }
////      }
//    }
//
//    override def needsScores(): Boolean = false
//
//    override def doEquals(other: function.ScoreFunction): Boolean = other match {
//      case f: KnnScoreFunctionBuilder.ScoreFunction => query.equals(f.query)
//      case _                                        => false
//    }
//
//    override def doHashCode(): Int = Objects.hash(query)
//  }
//
//  object ScoreFunction {
//    def apply(query: NearestNeighborsQuery, context: QueryShardContext): Try[ScoreFunction] = {
//      query.vec match {
//        case _: Vec.Indexed => Failure(new ElastiknnIllegalArgumentException(s"Score functions with indexed vectors are not yet supported"))
//        case _              => ???
//      }
//    }
//  }
}
