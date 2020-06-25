package com.klibisz.elastiknn.query

import java.util.Objects

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.models.SparseIndexedSimilarityFunction
import com.klibisz.elastiknn.storage.UnsafeSerialization
import org.apache.lucene.document.{Field, NumericDocValuesField}
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.lucene.search.function.{CombineFunction, FunctionScoreQuery, LeafScoreFunction, ScoreFunction}
import org.elasticsearch.index.mapper.MappedFieldType

object SparseIndexedQuery {

  private class SparseIndexedScoreFunction(val field: String, val queryVec: Vec.SparseBool, val simFunc: SparseIndexedSimilarityFunction)
      extends ScoreFunction(CombineFunction.REPLACE) {

    override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
      val numTrueDocValues: NumericDocValues = ctx.reader.getNumericDocValues(numTrueDocValueField(field))

      new LeafScoreFunction {
        override def score(docId: Int, intersection: Float): Double = {
          if (numTrueDocValues.advanceExact(docId)) {
            val numTrue = numTrueDocValues.longValue().toInt
            // Subtract one from intersection to account for value exists query in boolean query.
            simFunc(queryVec, intersection.toInt - 1, numTrue)
          } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")
        }

        override def explainScore(docId: Int, subQueryScore: Explanation): Explanation =
          Explanation.`match`(100, "Computing sparse indexed similarity")
      }

    }

    override def needsScores(): Boolean = true

    override def doEquals(other: ScoreFunction): Boolean = other match {
      case f: SparseIndexedScoreFunction => f.field == field && f.queryVec == queryVec && f.simFunc == simFunc
      case _                             => false
    }

    override def doHashCode(): Int = Objects.hash(field, queryVec, simFunc)
  }

  def apply(field: String, queryVec: Vec.SparseBool, simFunc: SparseIndexedSimilarityFunction): FunctionScoreQuery = {
    val isecQuery: BooleanQuery = {
      val builder = new BooleanQuery.Builder
      builder.add(new BooleanClause(new DocValuesFieldExistsQuery(numTrueDocValueField(field)), BooleanClause.Occur.MUST))
      queryVec.trueIndices.foreach { ti =>
        val term = new Term(field, new BytesRef(UnsafeSerialization.writeInt(ti)))
        val termQuery = new TermQuery(term)
        val constQuery = new ConstantScoreQuery(termQuery)
        val clause = new BooleanClause(constQuery, BooleanClause.Occur.SHOULD)
        builder.add(clause)
      }
      builder.build()
    }
    val f = new SparseIndexedScoreFunction(field, queryVec, simFunc)
    new FunctionScoreQuery(isecQuery, f, CombineFunction.REPLACE, 0f, Float.MaxValue)
  }

  def numTrueDocValueField(field: String): String = s"$field.num_true"

  def index(field: String, fieldType: MappedFieldType, vec: Vec.SparseBool): Seq[IndexableField] = {
    vec.trueIndices.map { ti =>
      new Field(field, UnsafeSerialization.writeInt(ti), fieldType)
    } ++ ExactQuery.index(field, vec) :+ new NumericDocValuesField(numTrueDocValueField(field), vec.trueIndices.length)
  }

}
