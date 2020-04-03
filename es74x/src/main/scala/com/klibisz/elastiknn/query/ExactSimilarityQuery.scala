package com.klibisz.elastiknn.query

import java.util
import java.util.Objects

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import com.klibisz.elastiknn.api.{ElasticsearchCodec, Vec}
import com.klibisz.elastiknn.models.ExactSimilarityFunction
import com.klibisz.elastiknn.storage.ByteArrayCodec
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index.{BinaryDocValues, IndexableField, LeafReaderContext, Term}
import org.apache.lucene.search._
import org.apache.lucene.util.BytesRef

class ExactSimilarityQuery[V <: Vec: ByteArrayCodec: ElasticsearchCodec](val field: String,
                                                                         val queryVec: V,
                                                                         val simFunc: ExactSimilarityFunction[V])
    extends Query {

  private val vectorDocValuesField = ExactSimilarityQuery.vectorDocValuesField(field)
  private val hasVectorQuery = new DocValuesFieldExistsQuery(vectorDocValuesField)

  class ExactSimilarityWeight(searcher: IndexSearcher) extends Weight(this) {
    private val hasVectorWeight = hasVectorQuery.createWeight(searcher, ScoreMode.COMPLETE, 1f)
    override def extractTerms(terms: util.Set[Term]): Unit = ()
    override def explain(context: LeafReaderContext, doc: Int): Explanation = ???
    override def scorer(context: LeafReaderContext): Scorer = {
      val vectorDocValues: BinaryDocValues = context.reader.getBinaryDocValues(vectorDocValuesField)
      val scorer = hasVectorWeight.scorer(context)
      val iterator = if (scorer == null) DocIdSetIterator.empty() else scorer.iterator()
      new ExactSimilarityScorer(this, iterator, vectorDocValues)
    }
    override def isCacheable(ctx: LeafReaderContext): Boolean = false
  }

  class ExactSimilarityScorer(weight: Weight, hasVectorIterator: DocIdSetIterator, vectorDocValues: BinaryDocValues)
      extends Scorer(weight) {
    override def getMaxScore(upTo: Int): Float = Float.MaxValue
    override def iterator(): DocIdSetIterator = hasVectorIterator
    override def docID(): Int = hasVectorIterator.docID()
    override def score(): Float = {
      val docId = this.docID()
      if (vectorDocValues.advanceExact(docId)) {
        val binaryValue = vectorDocValues.binaryValue
        val vecBytes = binaryValue.bytes.take(binaryValue.length)
        val scoreTry = for {
          storedVec <- implicitly[ByteArrayCodec[V]].apply(vecBytes)
          simScore <- simFunc(queryVec, storedVec)
        } yield simScore.score.toFloat
        scoreTry.get
      } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")
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

  def vectorDocValuesField(field: String): String = s"$field.$ELASTIKNN_NAME.vector"

  def index[V <: Vec: ByteArrayCodec](field: String, vec: V): Seq[IndexableField] = {
    Seq(new BinaryDocValuesField(vectorDocValuesField(field), new BytesRef(implicitly[ByteArrayCodec[V]].apply(vec))))
  }

}
