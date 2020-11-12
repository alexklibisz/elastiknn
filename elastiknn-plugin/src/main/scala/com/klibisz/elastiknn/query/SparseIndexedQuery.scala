package com.klibisz.elastiknn.query

import java.util.Objects

import com.klibisz.elastiknn.ElastiknnException.ElastiknnRuntimeException
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.models.{HashAndFreq, SparseIndexedSimilarityFunction}
import com.klibisz.elastiknn.storage.UnsafeSerialization
import org.apache.lucene.document.{Field, FieldType, NumericDocValuesField}
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.lucene.search.function.{CombineFunction, LeafScoreFunction, ScoreFunction}

class SparseIndexedQuery(field: String, queryVec: Vec.SparseBool, simFunc: SparseIndexedSimilarityFunction)
    extends ElastiknnQuery[Vec.SparseBool] {

  import SparseIndexedQuery._

  private val trueIndexTerms = queryVec.trueIndices.map(i => HashAndFreq.once(UnsafeSerialization.writeInt(i)))

  private val scoreFunction: java.util.function.Function[LeafReaderContext, MatchHashesAndScoreQuery.ScoreFunction] =
    (lrc: LeafReaderContext) => {
      val numericDocValues = lrc.reader.getNumericDocValues(numTrueDocValueField(field))
      (docId: Int, matchingTerms: Int) =>
        if (numericDocValues.advanceExact(docId)) {
          val numTrue = numericDocValues.longValue.toInt
          simFunc(queryVec, matchingTerms, numTrue)
        } else throw new ElastiknnRuntimeException(s"Couldn't advance to doc with id [$docId]")
    }

  override def toLuceneQuery(indexReader: IndexReader): Query =
    new MatchHashesAndScoreQuery(
      field,
      trueIndexTerms,
      indexReader.getDocCount(field),
      1f,
      indexReader,
      scoreFunction
    )

  override def toScoreFunction(indexReader: IndexReader): ScoreFunction = {

    val self = this

    new ScoreFunction(CombineFunction.REPLACE) {
      override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = new LeafScoreFunction {
        private val reader = ctx.reader()
        private val terms = reader.terms(field)
        private val termsEnum = terms.iterator()
        private val postings = trueIndexTerms.sorted.flatMap { h =>
          if (termsEnum.seekExact(new BytesRef(h.hash))) Some(termsEnum.postings(null, PostingsEnum.NONE))
          else None
        }
        private val scoreFunc = scoreFunction(ctx)

        override def score(docId: Int, subQueryScore: Float): Double = {
          val intersection = postings.count { p =>
            p.docID() != DocIdSetIterator.NO_MORE_DOCS && p.advance(docId) == docId
          }
          scoreFunc.score(docId, intersection)
        }

        override def explainScore(docId: Int, subQueryScore: Explanation): Explanation = {
          Explanation.`match`(
            score(docId, subQueryScore.getValue.floatValue()).toFloat,
            "Sparse indexed query score function."
          )
          ???
        }
      }

      override def needsScores(): Boolean = ???

      override def doEquals(other: ScoreFunction): Boolean = ???

      override def doHashCode(): Int = Objects.hash(self, indexReader)
    }
  }
}

object SparseIndexedQuery {

  def apply(field: String, queryVec: Vec.SparseBool, simFunc: SparseIndexedSimilarityFunction, indexReader: IndexReader): Query = {

    val terms = queryVec.trueIndices.map(i => HashAndFreq.once(UnsafeSerialization.writeInt(i)))

    val scoreFunction: java.util.function.Function[LeafReaderContext, MatchHashesAndScoreQuery.ScoreFunction] =
      (lrc: LeafReaderContext) => {
        val numericDocValues = lrc.reader.getNumericDocValues(numTrueDocValueField(field))
        (docId: Int, matchingTerms: Int) =>
          if (numericDocValues.advanceExact(docId)) {
            val numTrue = numericDocValues.longValue.toInt
            simFunc(queryVec, matchingTerms, numTrue)
          } else throw new ElastiknnRuntimeException(s"Couldn't advance to doc with id [$docId]")
      }

    new MatchHashesAndScoreQuery(
      field,
      terms,
      indexReader.getDocCount(field),
      1f,
      indexReader,
      scoreFunction
    )
  }

  def numTrueDocValueField(field: String): String = s"$field.num_true"

  def index(field: String, fieldType: FieldType, vec: Vec.SparseBool): Seq[IndexableField] = {
    vec.trueIndices.map { ti =>
      new Field(field, UnsafeSerialization.writeInt(ti), fieldType)
    } ++ ExactQuery.index(field, vec) :+ new NumericDocValuesField(numTrueDocValueField(field), vec.trueIndices.length)
  }

}
