package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.models.SparseIndexedSimilarityFunction
import com.klibisz.elastiknn.storage.UnsafeSerialization
import org.apache.lucene.document.{Field, NumericDocValuesField}
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.similarities.BooleanSimilarity
import org.apache.lucene.util.BytesRef

class SparseIndexedQuery private (val field: String, val queryVec: Vec.SparseBool, val simFunc: SparseIndexedSimilarityFunction)
    extends Query {

  private val numTrueDocValuesField: String = SparseIndexedQuery.numTrueDocValueField(field)

  private val isecQuery: BooleanQuery = {
    val builder = new BooleanQuery.Builder
    builder.add(new BooleanClause(new DocValuesFieldExistsQuery(numTrueDocValuesField), BooleanClause.Occur.MUST))
    queryVec.trueIndices.foreach { ti =>
      val term = new Term(field, new BytesRef(UnsafeSerialization.writeInt(ti)))
      val termQuery = new TermQuery(term)
      val constQuery = new ConstantScoreQuery(termQuery)
      val clause = new BooleanClause(constQuery, BooleanClause.Occur.FILTER)
      builder.add(clause)
    }
    builder.setMinimumNumberShouldMatch(1)
    builder.build()
  }

  class SparseIndexedWeight(searcher: IndexSearcher) extends Weight(this) {
    searcher.setSimilarity(new BooleanSimilarity)
    private val intersectionWeight = isecQuery.createWeight(searcher, ScoreMode.COMPLETE, 1f)
    override def extractTerms(terms: java.util.Set[Term]): Unit = ()
    override def explain(context: LeafReaderContext, doc: Int): Explanation = ???
    override def scorer(context: LeafReaderContext): Scorer = {
      val numTrueDocValues: NumericDocValues = context.reader.getNumericDocValues(numTrueDocValuesField)
      val scorer = intersectionWeight.scorer(context)
      new SparseIndexedScorer(this, scorer, numTrueDocValues)
    }
    override def isCacheable(ctx: LeafReaderContext): Boolean = false
  }

  class SparseIndexedScorer(weight: Weight, intersectionScorer: Scorer, numericDocValues: NumericDocValues) extends Scorer(weight) {
    override val iterator: DocIdSetIterator = if (intersectionScorer == null) DocIdSetIterator.empty() else intersectionScorer.iterator()
    override def docID(): Int = iterator.docID()
    override def getMaxScore(upTo: Int): Float = Float.MaxValue
    override def score(): Float = {
      val intersection = intersectionScorer.score() - 1 // Subtract one to account for doc values field.
      val docId = docID()
      if (numericDocValues.advanceExact(docId)) {
        val numTrue = numericDocValues.longValue().toInt
        simFunc(queryVec, intersection.toInt, numTrue).toFloat
      } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")
    }
  }

  override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight = {
    new SparseIndexedWeight(searcher)
  }

  override def toString(field: String): String =
    s"SparseIndexedQuery for field [$field], query vector [${ElasticsearchCodec.nospaces(queryVec)}], similarity [${simFunc.similarity}]"

  override def equals(other: Any): Boolean = other match {
    case q: SparseIndexedQuery => q.field == field && q.queryVec == queryVec && q.simFunc == simFunc
    case _                     => false
  }

  override def hashCode(): Int = java.util.Objects.hash(this.field, this.queryVec, this.simFunc)
}

object SparseIndexedQuery {

  def apply(field: String, queryVec: Vec.SparseBool, simFunc: SparseIndexedSimilarityFunction): Query = {
    new SparseIndexedQuery(field, queryVec, simFunc)
  }

  def numTrueDocValueField(field: String): String = s"$field.num_true"

  def index(field: String, vec: Vec.SparseBool): Seq[IndexableField] = {
    vec.trueIndices.map { ti =>
      new Field(field, UnsafeSerialization.writeInt(ti), VectorMapper.simpleTokenFieldType)
    } ++ ExactQuery.index(field, vec) :+ new NumericDocValuesField(numTrueDocValueField(field), vec.trueIndices.length)
  }

}
