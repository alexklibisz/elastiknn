package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.ExactSimilarityFunction
import com.klibisz.elastiknn.storage.{StoredVec, StoredVecReader}
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index.{IndexReader, IndexableField, LeafReaderContext}
import org.apache.lucene.search.{Explanation, FieldExistsQuery, Query}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.lucene.search.function.{CombineFunction, FunctionScoreQuery, LeafScoreFunction, ScoreFunction}

import java.util.Objects

class ExactQuery[V <: Vec, S <: StoredVec](field: String, queryVec: V, simFunc: ExactSimilarityFunction[V, S])(implicit
    codec: StoredVec.Codec[V, S]
) extends ElastiknnQuery[V] {

  override def toLuceneQuery(indexReader: IndexReader): Query = {
    val subQuery = new FieldExistsQuery(field)
    val func = toScoreFunction(indexReader)
    new FunctionScoreQuery(subQuery, func)
  }

  override def toScoreFunction(indexReader: IndexReader): ScoreFunction = {

    val self = this

    new ScoreFunction(CombineFunction.REPLACE) {

      override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
        val reader = new StoredVecReader[S](ctx, field)
        new LeafScoreFunction {
          override def score(docId: Int, subQueryScore: Float): Double = {
            val storedVec = reader(docId)
            simFunc(queryVec, storedVec)
          }
          override def explainScore(docId: Int, subQueryScore: Explanation): Explanation =
            Explanation.`match`(
              score(docId, subQueryScore.getValue.floatValue()),
              s"Elastiknn exact score function. Returns the exact similarity for each doc."
            )
        }
      }

      override def needsScores(): Boolean = false

      override def doEquals(other: ScoreFunction): Boolean = false

      override def doHashCode(): Int = Objects.hash(self, indexReader)

    }
  }
}

object ExactQuery {
  def index[V <: Vec: StoredVec.Encoder](field: String, vec: V): Seq[IndexableField] = {
    val storedVec = implicitly[StoredVec.Encoder[V]].apply(vec)
    Seq(new BinaryDocValuesField(field, new BytesRef(storedVec)))
  }
}
