package com.klibisz.elastiknn.query

import java.util

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.models.SparseIndexedSimilarityFunction
import com.klibisz.elastiknn.storage.ByteArrayCodec
import org.apache.lucene.document.{Field, FieldType, StoredField}
import org.apache.lucene.index.{IndexOptions, IndexableField, LeafReaderContext, Term}
import org.apache.lucene.search.similarities.BooleanSimilarity
import org.apache.lucene.search._
import org.apache.lucene.util.BytesRef

class SparseIndexedQuery(field: String, queryVec: Vec.SparseBool, simFunc: SparseIndexedSimilarityFunction) extends Query {

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
    override def scorer(context: LeafReaderContext): Scorer =
      new SparseIndexedScorer(this, searcher, intersectionWeight.scorer(context))
    override def isCacheable(ctx: LeafReaderContext): Boolean = false
  }

  class SparseIndexedScorer(weight: Weight, searcher: IndexSearcher, intersectionScorer: Scorer) extends Scorer(weight) {
    override val iterator: DocIdSetIterator = if (intersectionScorer != null) intersectionScorer.iterator() else DocIdSetIterator.empty()
    override def getMaxScore(upTo: Int): Float = Float.MaxValue
    override def score(): Float = {
      val intersection = intersectionScorer.score()
      val docId = iterator.docID()
      val doc = searcher.doc(docId)
      val numTrue = doc.getField(storedNumTrueField).numericValue().intValue()
      val scoreTry = simFunc(queryVec, intersection.toInt, numTrue)
      scoreTry.get.score.toFloat
    }
    override def docID(): Int = iterator.docID()
  }

  override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight =
    new SparseIndexedWeight(searcher)

  override def toString(field: String): String = ???
  override def equals(obj: Any): Boolean = ???
  override def hashCode(): Int = ???
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
    ExactSimilarityQuery.index(field, vec) ++ vec.trueIndices.map { ti =>
      new Field(indexedIndicesFieldName, ByteArrayCodec.encode(ti), indexedIndicesFieldType)
    } :+ new StoredField(storedNumTrueField(field), vec.trueIndices.length)
  }

}
