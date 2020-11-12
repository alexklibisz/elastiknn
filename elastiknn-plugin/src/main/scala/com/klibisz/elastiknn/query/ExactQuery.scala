package com.klibisz.elastiknn.query

import java.util.Objects

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.ExactSimilarityFunction
import com.klibisz.elastiknn.storage.{StoredVec, StoredVecReader}
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index.{IndexableField, LeafReaderContext}
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Explanation, Query}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.lucene.search.function.{CombineFunction, FunctionScoreQuery, LeafScoreFunction, ScoreFunction}
import org.elasticsearch.index.query.QueryShardContext

class ExactQuery[V <: Vec, S <: StoredVec](field: String, queryVec: V, simFunc: ExactSimilarityFunction[V, S])(
    implicit codec: StoredVec.Codec[V, S])
    extends ElastiknnQuery[V] {

  override def toLuceneQuery(queryShardContext: QueryShardContext): Query = {
    val subQuery = new DocValuesFieldExistsQuery(field)
    val func = toScoreFunction(queryShardContext)
    new FunctionScoreQuery(subQuery, func)
  }

  override def toScoreFunction(queryShardContext: QueryShardContext): ScoreFunction =
    new ScoreFunction(CombineFunction.REPLACE) {

      override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
        val reader = new StoredVecReader[S](ctx, field)
        new LeafScoreFunction {
          override def score(docId: Int, subQueryScore: Float): Double = {
            val storedVec = reader(docId)
            simFunc(queryVec, storedVec)
          }
          override def explainScore(docId: Int, subQueryScore: Explanation): Explanation =
            Explanation.`match`(score(docId, subQueryScore.getValue.floatValue()), s"Elastiknn exact query")
        }
      }

      override def needsScores(): Boolean = false

      override def doEquals(other: ScoreFunction): Boolean = false

      override def doHashCode(): Int = Objects.hash(field, queryVec, simFunc)

    }
}

object ExactQuery {
  def index[V <: Vec: StoredVec.Encoder](field: String, vec: V): Seq[IndexableField] = {
    val storedVec = implicitly[StoredVec.Encoder[V]].apply(vec)
    Seq(new BinaryDocValuesField(field, new BytesRef(storedVec)))
  }
}
//
//import java.util.Objects
//
//import com.klibisz.elastiknn.ElastiknnException.ElastiknnRuntimeException
//import com.klibisz.elastiknn.api.Vec
//import com.klibisz.elastiknn.models.ExactSimilarityFunction
//import com.klibisz.elastiknn.storage.{StoredVec, StoredVecReader}
//import com.klibisz.elastiknn.storage.StoredVec.Decoder
//import org.apache.lucene.document.BinaryDocValuesField
//import org.apache.lucene.index.{IndexableField, LeafReaderContext}
//import org.apache.lucene.search.{DocValuesFieldExistsQuery, Explanation}
//import org.apache.lucene.util.BytesRef
//import org.elasticsearch.common.lucene.search.function._
//
//object ExactQuery {
//
//  private class ExactScoreFunction[V <: Vec, S <: StoredVec](val field: String, val queryVec: V, val simFunc: ExactSimilarityFunction[V, S])(
//      implicit codec: StoredVec.Codec[V, S])
//      extends ScoreFunction(CombineFunction.REPLACE) {
//
//    override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
//      val cachedReader = new StoredVecReader[S](ctx, field)
//      new LeafScoreFunction {
//        override def score(docId: Int, subQueryScore: Float): Double = {
//          val storedVec = cachedReader(docId)
//          simFunc(queryVec, storedVec)
//        }
//        override def explainScore(docId: Int, subQueryScore: Explanation): Explanation =
//          Explanation.`match`(score(docId, subQueryScore.getValue.floatValue()), s"Elastiknn exact query")
//      }
//    }
//
//    override def needsScores(): Boolean = false
//
//    override def doEquals(other: ScoreFunction): Boolean = other match {
//      case f: ExactScoreFunction[V, S] => field == f.field && queryVec == f.queryVec && simFunc == f.simFunc
//      case _                           => false
//    }
//
//    override def doHashCode(): Int = Objects.hash(field, queryVec, simFunc)
//  }
//
//  /**
//    * Instantiate an exact query, implemented as an Elasticsearch [[FunctionScoreQuery]].
//    */
//  private def apply[V <: Vec, S <: StoredVec](field: String, queryVec: V, simFunc: ExactSimilarityFunction[V, S])(
//      implicit codec: StoredVec.Codec[V, S]): FunctionScoreQuery = {
//    val subQuery = new DocValuesFieldExistsQuery(field)
//    val func = new ExactScoreFunction(field, queryVec, simFunc)
//    new FunctionScoreQuery(subQuery, func)
//  }
//
//  /**
//    * Creates and returns a single indexable field that stores the vector contents as a [[BinaryDocValuesField]].
//    */
//  private def index[V <: Vec: StoredVec.Encoder](field: String, vec: V): Seq[IndexableField] = {
//    val storedVec = implicitly[StoredVec.Encoder[V]].apply(vec)
//    Seq(new BinaryDocValuesField(field, new BytesRef(storedVec)))
//  }
//
//}
