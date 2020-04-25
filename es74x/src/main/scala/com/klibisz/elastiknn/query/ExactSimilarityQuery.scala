package com.klibisz.elastiknn.query

import java.util
import java.util.Objects

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import com.klibisz.elastiknn.api.{ElasticsearchCodec, Vec}
import com.klibisz.elastiknn.models.ExactSimilarityFunction
import com.klibisz.elastiknn.storage.ByteArrayCodec
import com.klibisz.elastiknn.storage.VecCache.{ContextCache, DocIdCache}
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index.{BinaryDocValues, IndexableField, LeafReaderContext, Term}
import org.apache.lucene.search._
import org.apache.lucene.util.BytesRef

class ExactSimilarityQuery[V <: Vec: ByteArrayCodec: ElasticsearchCodec](val field: String,
                                                                         val queryVec: V,
                                                                         val simFunc: ExactSimilarityFunction[V],
                                                                         val contextCache: ContextCache[V])
    extends Query {

  private val vectorDocValuesField = ExactSimilarityQuery.vectorDocValuesField(field)
//  private val hasVectorQuery = new DocValuesFieldExistsQuery(vectorDocValuesField)

  class ExactSimilarityWeight(searcher: IndexSearcher) extends Weight(this) {
//    private val hasVectorWeight = hasVectorQuery.createWeight(searcher, ScoreMode.COMPLETE, 1f)
    override def extractTerms(terms: util.Set[Term]): Unit = ()
    override def explain(context: LeafReaderContext, doc: Int): Explanation = ???
    override def scorer(context: LeafReaderContext): Scorer = {
      val vectorDocValues: BinaryDocValues = context.reader.getBinaryDocValues(vectorDocValuesField)
      val iterator = DocIdSetIterator.all(context.reader().maxDoc())
//      val scorer = hasVectorWeight.scorer(context)
//      val iterator = if (scorer == null) DocIdSetIterator.empty() else scorer.iterator()
      new ExactSimilarityScorer(this, vectorDocValues, iterator, contextCache.get(context))
    }
    override def isCacheable(ctx: LeafReaderContext): Boolean = false
  }

  class ExactSimilarityScorer(weight: Weight, vectorDocValues: BinaryDocValues, hasVecIterator: DocIdSetIterator, docIdCache: DocIdCache[V])
      extends Scorer(weight) {
    override def getMaxScore(upTo: Int): Float = Float.MaxValue
    override def iterator(): DocIdSetIterator = hasVecIterator
    override def docID(): Int = hasVecIterator.docID()
    override def score(): Float = {
      val docId = this.docID()
      val storedVec = docIdCache.get(
        docId,
        () => {
          if (vectorDocValues.advanceExact(docId)) {
            val binaryValue = vectorDocValues.binaryValue
            val vecBytes = binaryValue.bytes.take(binaryValue.length)
            implicitly[ByteArrayCodec[V]].apply(vecBytes).get
          } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")
        }
      )
      val scoreTry = simFunc(queryVec, storedVec)
      scoreTry.get.score.toFloat
    }
  }

  override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight = new ExactSimilarityWeight(searcher)

  override def toString(field: String): String =
    s"ExactSimilarityQuery for field [$field], query vector [${nospaces(queryVec)}], similarity [${simFunc.similarity}]"

  override def equals(other: Any): Boolean = other match {
    case q: ExactSimilarityQuery[V] => q.field == field && q.queryVec == queryVec && q.simFunc == simFunc
    case _                          => false
  }

  override def hashCode(): Int = Objects.hashCode(field, queryVec, simFunc)

}

object ExactSimilarityQuery {

  // Docvalue fields can have a custom name, but "regular" values (e.g. Terms) must keep the name of the field.
  def vectorDocValuesField(field: String): String = s"$field.$ELASTIKNN_NAME.vector"

  def index[V <: Vec: ByteArrayCodec](field: String, vec: V): Seq[IndexableField] = {
    Seq(new BinaryDocValuesField(vectorDocValuesField(field), new BytesRef(implicitly[ByteArrayCodec[V]].apply(vec))))
  }

}
