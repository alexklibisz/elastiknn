package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.{ExactSimilarityFunction, HashAndFreq}
import com.klibisz.elastiknn.storage.StoredVec.Decoder
import com.klibisz.elastiknn.storage.{StoredVec, StoredVecReader}
import org.apache.lucene.document.{Field, FieldType}
import org.apache.lucene.index.{IndexReader, IndexableField, LeafReaderContext, PostingsEnum}
import org.apache.lucene.search.{DocIdSetIterator, Explanation, MatchHashesAndScoreQuery, Query}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.lucene.search.function.{CombineFunction, LeafScoreFunction, ScoreFunction}

import java.util.Objects
import scala.collection.mutable.ListBuffer

final class HashingQuery[V <: Vec, S <: StoredVec: Decoder](
    field: String,
    queryVec: V,
    candidates: Int,
    hashes: Array[HashAndFreq],
    simFunc: ExactSimilarityFunction[V, S]
) extends ElastiknnQuery {
  override def toLuceneQuery(indexReader: IndexReader): Query = {
    val scoreFunction: java.util.function.Function[LeafReaderContext, MatchHashesAndScoreQuery.ScoreFunction] =
      (lrc: LeafReaderContext) => {
        val reader = new StoredVecReader[S](lrc, field)
        (docId: Int, _: Int) =>
          val storedVec = reader(docId)
          simFunc(queryVec, storedVec)
      }
    new MatchHashesAndScoreQuery(
      field,
      hashes,
      candidates,
      indexReader,
      scoreFunction
    )
  }

  /** Note that this score function does not re-score the top candidates. The final score produced is `(max possible score for this
    * similarity * (number of matching hashes / total number of hashes)`. This is necessary because a ScoreFunction can only evaluate one
    * doc at a time and must immediately score it.
    */
  override def toScoreFunction(indexReader: IndexReader): ScoreFunction = {

    val self = this

    new ScoreFunction(CombineFunction.REPLACE) {
      override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = new LeafScoreFunction {

        // First build up an array of postings, one per hash. Then in the score() method, try to advance each posting
        // to the given doc ID. If the posting advances to the doc ID, that means this hash is present for the doc ID.
        private val reader = ctx.reader()
        private val terms = reader.terms(field)
        private val termsEnum = terms.iterator()
        private val postings: Seq[PostingsEnum] = {
          val buf = new ListBuffer[PostingsEnum]()
          hashes.sorted.foreach { h =>
            if (termsEnum.seekExact(new BytesRef(h.hash))) buf.prepend(termsEnum.postings(null, PostingsEnum.NONE))
            else None
          }
          buf.toList.reverse
        }

        override def score(docId: Int, subQueryScore: Float): Double = {
          val intersection = postings.count { p => p.docID() != DocIdSetIterator.NO_MORE_DOCS && p.advance(docId) == docId }
          simFunc.maxScore * (intersection * 1d / hashes.length)
        }

        override def explainScore(docId: Int, subQueryScore: Explanation): Explanation =
          Explanation.`match`(
            score(docId, subQueryScore.getValue.floatValue()).toFloat,
            "Hashing query score function. Returns an approximation of the exact similarity for each doc: (max score for this similarity * proportion of hashes in this vec matching hashes in query vec)"
          )
      }

      override def needsScores(): Boolean = false

      override def doEquals(other: ScoreFunction): Boolean = false

      override def doHashCode(): Int = Objects.hash(self, indexReader)
    }
  }
}

object HashingQuery {

  def index[V <: Vec: StoredVec.Encoder](
      field: String,
      fieldType: FieldType,
      vec: V,
      hashes: Array[HashAndFreq]
  ): Seq[IndexableField] = {
    val buffer = ListBuffer.empty[IndexableField]
    hashes.foreach { h =>
      (0 until h.freq).foreach(_ => buffer.prepend(new Field(field, h.hash, fieldType)))
    }
    buffer.prepend(ExactQuery.index(field, vec)).toList
  }
}
