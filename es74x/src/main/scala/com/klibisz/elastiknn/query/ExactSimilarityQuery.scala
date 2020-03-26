package com.klibisz.elastiknn.query

import java.util
import java.util.Objects

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.{ExactSimilarityFunction, ExactSimilarityScore}
import com.klibisz.elastiknn.storage.ByteArrayCodec
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index.{IndexableField, LeafReaderContext, Term}
import org.apache.lucene.search._
import org.apache.lucene.util.BytesRef
import org.elasticsearch.index.query.QueryShardContext

class ExactSimilarityQuery[V <: Vec](val field: String, val queryVec: V, val simFunc: ExactSimilarityFunction[V])(
    implicit codec: ByteArrayCodec[V])
    extends Query {

  private val storedField = ExactSimilarityQuery.storedVectorField(field)
  private val existsQuery: DocValuesFieldExistsQuery = new DocValuesFieldExistsQuery(storedField)

  class ExactSimilarityWeight(searcher: IndexSearcher) extends Weight(this) {
    private val existsWeight = existsQuery.createWeight(searcher, ScoreMode.COMPLETE, 1f)
    override def extractTerms(terms: util.Set[Term]): Unit = ()
    override def explain(context: LeafReaderContext, doc: Int): Explanation = ???
    override def scorer(context: LeafReaderContext): Scorer = {
      val existsScorer = existsWeight.scorer(context)
      val existsIter = if (existsScorer != null) existsScorer.iterator() else DocIdSetIterator.empty()
      new ExactSimilarityScorer(this, searcher, context, existsIter)
    }
    override def isCacheable(ctx: LeafReaderContext): Boolean = false
  }

  class ExactSimilarityScorer(weight: Weight, searcher: IndexSearcher, context: LeafReaderContext, iterator: DocIdSetIterator)
      extends Scorer(weight) {
    private val reader = context.reader()
    private val binaryDocValues = reader.getBinaryDocValues(storedField)
    override def iterator(): DocIdSetIterator = iterator
    override def getMaxScore(upTo: Int): Float = Float.MaxValue
    override def score(): Float = {
      val docId = iterator.docID()
      if (binaryDocValues.advanceExact(docId)) {
        val bv: BytesRef = binaryDocValues.binaryValue()
        val barr = bv.bytes.take(bv.length)
        val storedVec: V = codec(barr).get
        val simScore: ExactSimilarityScore = simFunc(queryVec, storedVec).get

        val doc = searcher.doc(docId)
        val foo = doc.getField("foo").numericValue().intValue()
        val bar = doc.getField("bar").binaryValue().bytes
        val baz = codec(bar).get
        simScore.score.toFloat
      } else 0f
    }
    override def docID(): Int = iterator.docID()
  }

  override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight = new ExactSimilarityWeight(searcher)

  override def toString(field: String): String =
    s"ExactSimilarityQuery for field [$field], query vector [$queryVec], similarity [${simFunc.similarity}]"

  override def equals(other: Any): Boolean = other match {
    case q: ExactSimilarityQuery[V] => q.storedField == storedField && q.queryVec == queryVec && q.simFunc == simFunc
    case _                          => false
  }

  override def hashCode(): Int = Objects.hashCode(storedField, queryVec, simFunc)
}

object ExactSimilarityQuery {

  def storedVectorField(field: String): String = s"$field.$ELASTIKNN_NAME.vector"

  def index[V <: Vec](field: String, vec: V)(implicit codec: ByteArrayCodec[V]): Seq[IndexableField] =
    Seq(new BinaryDocValuesField(storedVectorField(field), new BytesRef(codec(vec))))

}
