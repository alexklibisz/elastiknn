package com.klibisz.elastiknn.query

import java.util
import java.util.Objects

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.models.SparseIndexedSimilarityFunction
import com.klibisz.elastiknn.storage.ByteArrayCodec
import org.apache.lucene.document.{Field, FieldType, NumericDocValuesField, StoredField}
import org.apache.lucene.index.{IndexOptions, IndexableField, LeafReaderContext, NumericDocValues, Term}
import org.apache.lucene.search.similarities.BooleanSimilarity
import org.apache.lucene.search._
import org.apache.lucene.util.BytesRef

class SparseIndexedQuery(val field: String, val queryVec: Vec.SparseBool, val simFunc: SparseIndexedSimilarityFunction) extends Query {

  private val indexedIndicesField: String = SparseIndexedQuery.indexedIndicesField(field)
  private val storedNumTrueField: String = SparseIndexedQuery.storedNumTrueField(field)

  private val intersectionQuery: BooleanQuery = {
    val builder = new BooleanQuery.Builder
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
      val ndv: NumericDocValues = context.reader().getNumericDocValues(storedNumTrueField)
      new SparseIndexedScorer(this, searcher, intersectionWeight.scorer(context), ndv)
    }
    override def isCacheable(ctx: LeafReaderContext): Boolean = false
  }

  class SparseIndexedScorer(weight: Weight, searcher: IndexSearcher, intersectionScorer: Scorer, numericDocValues: NumericDocValues)
      extends Scorer(weight) {
    override val iterator: DocIdSetIterator = if (intersectionScorer != null) intersectionScorer.iterator() else DocIdSetIterator.empty()
    override def getMaxScore(upTo: Int): Float = Float.MaxValue
    override def score(): Float = {
      val intersection = intersectionScorer.score()
      if (numericDocValues.advanceExact(docID())) {
        val numTrue = numericDocValues.longValue().toInt
        val scoreTry = simFunc(queryVec, intersection.toInt, numTrue)
        scoreTry.get.score.toFloat
      } else 0f
    }
    override def docID(): Int = iterator.docID()
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

  def indexedIndicesField(field: String): String = s"$field.$ELASTIKNN_NAME.true_indices"
  def storedNumTrueField(field: String): String = s"$field.$ELASTIKNN_NAME.num_true"

  val indexedIndicesFieldType: FieldType = {
    val ft = new FieldType
    ft.setIndexOptions(IndexOptions.DOCS)
    ft.setTokenized(false)
    ft
  }

  def index(field: String, vec: Vec.SparseBool): Seq[IndexableField] = {
    val indexedIndicesFieldName = this.indexedIndicesField(field)
//    ExactSimilarityQuery.index(field, vec) ++ vec.trueIndices.map { ti =>
//      new Field(indexedIndicesFieldName, ByteArrayCodec.encode(ti), indexedIndicesFieldType)
//    } :+ new StoredField(storedNumTrueField(field), vec.trueIndices.length)

    ExactSimilarityQuery.index(field, vec) ++ vec.trueIndices.map { ti =>
      new Field(indexedIndicesFieldName, ByteArrayCodec.encode(ti), indexedIndicesFieldType)
    } :+ new NumericDocValuesField(storedNumTrueField(field), vec.trueIndices.length)

  }

}
