package com.klibisz.elastiknn.query

import java.util
import java.util.Objects

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.models.SparseIndexedSimilarityFunction
import com.klibisz.elastiknn.storage.ByteArrayCodec
import org.apache.lucene.document.{Field, FieldType, NumericDocValuesField}
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.similarities.BooleanSimilarity
import org.apache.lucene.util.BytesRef

class SparseIndexedQuery(val field: String, val queryVec: Vec.SparseBool, val simFunc: SparseIndexedSimilarityFunction) extends Query {

  private val indexedIndicesField: String = SparseIndexedQuery.trueIndicesTermField(field)
  private val numTrueDocValueField: String = SparseIndexedQuery.numTrueDocValueField(field)

  private val intersectionQuery: BooleanQuery = {
    val builder = new BooleanQuery.Builder
    builder.add(new BooleanClause(new DocValuesFieldExistsQuery(numTrueDocValueField), BooleanClause.Occur.MUST))
    queryVec.trueIndices.foreach { ti =>
      val term = new Term(indexedIndicesField, new BytesRef(ByteArrayCodec.encode(ti)))
      val termQuery = new TermQuery(term)
      val clause = new BooleanClause(termQuery, BooleanClause.Occur.SHOULD)
      builder.add(clause)
    }
    builder.build()
  }

  class SparseIndexedWeight(searcher: IndexSearcher) extends Weight(this) {
    searcher.setSimilarity(new BooleanSimilarity)
    private val intersectionWeight = intersectionQuery.createWeight(searcher, ScoreMode.COMPLETE, 1f)
    override def extractTerms(terms: util.Set[Term]): Unit = ()
    override def explain(context: LeafReaderContext, doc: Int): Explanation = ???
    override def scorer(context: LeafReaderContext): Scorer = {
      val numTrueDocValues: NumericDocValues = context.reader.getNumericDocValues(numTrueDocValueField)
      val scorer = intersectionWeight.scorer(context)
      new SparseIndexedScorer(this, scorer, numTrueDocValues)
    }
    override def isCacheable(ctx: LeafReaderContext): Boolean = false
  }

  class SparseIndexedScorer(weight: Weight, intersectionScorer: Scorer, numericDocValues: NumericDocValues)
      extends FilterScorer(intersectionScorer, weight) {
    override def getMaxScore(upTo: Int): Float = Float.MaxValue
    override def score(): Float = {
      val intersection = intersectionScorer.score() - 1 // Subtract one to account for doc values field.
      val docId = docID()
      if (numericDocValues.advanceExact(docID())) {
        val numTrue = numericDocValues.longValue().toInt
        val scoreTry = simFunc(queryVec, intersection.toInt, numTrue)
        scoreTry.get.score.toFloat
      } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")
    }
  }

  override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight =
    new SparseIndexedWeight(searcher)

  override def toString(field: String): String =
    s"SparseIndexedQuery for field [$field], query vector [${ElasticsearchCodec.nospaces(queryVec)}], similarity [${simFunc.similarity}]"

  override def equals(other: Any): Boolean = other match {
    case q: SparseIndexedQuery => q.field == field && q.queryVec == queryVec && q.simFunc == simFunc
    case _                     => false
  }

  override def hashCode(): Int = Objects.hashCode(field, queryVec, simFunc)
}

object SparseIndexedQuery {

  def trueIndicesTermField(field: String): String = s"$field.$ELASTIKNN_NAME.true_indices"
  def numTrueDocValueField(field: String): String = s"$field.$ELASTIKNN_NAME.num_true"

  private val indexedIndicesFieldType: FieldType = {
    val ft = new FieldType
    ft.setIndexOptions(IndexOptions.DOCS)
    ft.setTokenized(false)
    ft
  }

  def index(field: String, vec: Vec.SparseBool): Seq[IndexableField] = {
    val indexedIndicesFieldName = this.trueIndicesTermField(field)
    ExactSimilarityQuery.index(field, vec) ++ vec.trueIndices.map { ti =>
      new Field(indexedIndicesFieldName, ByteArrayCodec.encode(ti), indexedIndicesFieldType)
    } :+ new NumericDocValuesField(numTrueDocValueField(field), vec.trueIndices.length)
  }

}
