package com.klibisz.elastiknn.query

import java.util
import java.util.Objects

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api.{ElasticsearchCodec, Vec}
import com.klibisz.elastiknn.models.ExactSimilarityFunction
import com.klibisz.elastiknn.storage.ByteArrayCodec
import org.apache.lucene.document.StoredField
import org.apache.lucene.index.{IndexableField, LeafReaderContext, Term}
import org.apache.lucene.search._
import org.apache.lucene.util.BytesRef

class ExactSimilarityQuery[V <: Vec: ElasticsearchCodec: ByteArrayCodec](val field: String,
                                                                         val queryVec: V,
                                                                         val simFunc: ExactSimilarityFunction[V])
    extends Query {

  private val storedVectorField = ExactSimilarityQuery.storedVectorField(field)

  class ExactSimilarityWeight(searcher: IndexSearcher) extends Weight(this) {
    override def extractTerms(terms: util.Set[Term]): Unit = ()
    override def explain(context: LeafReaderContext, doc: Int): Explanation = ???
    override def scorer(context: LeafReaderContext): Scorer = {
      val iter = DocIdSetIterator.all(context.reader().maxDoc())
      new ExactSimilarityScorer(this, searcher, iter)
    }
    override def isCacheable(ctx: LeafReaderContext): Boolean = false
  }

  class ExactSimilarityScorer(weight: Weight, searcher: IndexSearcher, iterator: DocIdSetIterator) extends Scorer(weight) {
    override def iterator(): DocIdSetIterator = iterator
    override def getMaxScore(upTo: Int): Float = Float.MaxValue
    override def score(): Float = {
      val docId = iterator.docID()
      val doc = searcher.doc(docId)
      val vecBytes = doc.getField(storedVectorField).binaryValue.bytes
      val vec = implicitly[ByteArrayCodec[V]].apply(vecBytes).get
      val scoreTry = simFunc(queryVec, vec)
      scoreTry.get.score.toFloat
    }
    override def docID(): Int = iterator.docID()
  }

  override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight = new ExactSimilarityWeight(searcher)

  override def toString(field: String): String =
    s"ExactSimilarityQuery for field [$field], query vector [${ElasticsearchCodec.nospaces(queryVec)}], similarity [${simFunc.similarity}]"

  override def equals(other: Any): Boolean = other match {
    case q: ExactSimilarityQuery[V] => q.field == field && q.queryVec == queryVec && q.simFunc == simFunc
    case _                          => false
  }

  override def hashCode(): Int = Objects.hashCode(field, queryVec, simFunc)

}

object ExactSimilarityQuery {

  def storedVectorField(field: String): String = s"$field.$ELASTIKNN_NAME.vector"

  def index[V <: Vec](field: String, vec: V)(implicit codec: ByteArrayCodec[V]): Seq[IndexableField] =
    Seq(new StoredField(storedVectorField(field), new BytesRef(codec(vec))))

}
