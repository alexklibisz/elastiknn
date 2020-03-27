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
import org.elasticsearch.index.query.QueryShardContext

class ExactSimilarityQuery[V <: Vec: ByteArrayCodec: ElasticsearchCodec](val queryShardContext: QueryShardContext,
                                                                         val field: String,
                                                                         val queryVec: V,
                                                                         val simFunc: ExactSimilarityFunction[V])
    extends Query {

  private val storedVectorField = ExactSimilarityQuery.storedVectorField(field)
//  private val existsQuery = new ExistsQueryBuilder("i").toQuery(queryShardContext)
  private val matchAllQuery = new MatchAllDocsQuery()

  class ExactSimilarityWeight(searcher: IndexSearcher) extends Weight(this) {
    private val matchAllWeight = matchAllQuery.createWeight(searcher, ScoreMode.COMPLETE, 1f)
    override def extractTerms(terms: util.Set[Term]): Unit = ()
    override def explain(context: LeafReaderContext, doc: Int): Explanation = ???
    override def scorer(context: LeafReaderContext): Scorer = {
//      val iter = DocIdSetIterator.all(context.reader().maxDoc())
      val bdv: BinaryDocValues = context.reader().getBinaryDocValues(storedVectorField)
      val scorer = matchAllWeight.scorer(context)
      new ExactSimilarityScorer(this, searcher, scorer, bdv)
    }
    override def isCacheable(ctx: LeafReaderContext): Boolean = false
  }

  class ExactSimilarityScorer(weight: Weight, searcher: IndexSearcher, scorer: Scorer, binaryDocValues: BinaryDocValues)
      extends FilterScorer(scorer, weight) {

    override def getMaxScore(upTo: Int): Float = Float.MaxValue

    override def score(): Float = {
      if (binaryDocValues.advanceExact(this.docID())) {
        val vecBytes = binaryDocValues.binaryValue.bytes
        val vec: V = implicitly[ByteArrayCodec[V]].apply(vecBytes.take(vecBytes.length)).get
        val score = simFunc(queryVec, vec)
        score.get.score.toFloat
        //      val vecString = doc.getField(storedVectorField).stringValue()
        //      val vec = ElasticsearchCodec.decodeB64Get[V](vecString)
        //      val vecBinaryValue = doc.getField(storedVectorField).binaryValue()
        //      val vecBytes = vecBinaryValue.bytes.take(vecBinaryValue.length)
        //      val vec = implicitly[ByteArrayCodec[V]].apply(vecBytes).get
        //      val scoreTry = simFunc(queryVec, vec)
        //      scoreTry.get.score.toFloat
      } else 0f

//      val docId = docID()
//      val doc = searcher.doc(docId)
//      val vecString = doc.getField(storedVectorField).stringValue()
//      val vec = ElasticsearchCodec.decodeB64Get[V](vecString)
//      val vecBinaryValue = doc.getField(storedVectorField).binaryValue()
//      val vecBytes = vecBinaryValue.bytes.take(vecBinaryValue.length)
//      val vec = implicitly[ByteArrayCodec[V]].apply(vecBytes).get
//      val scoreTry = simFunc(queryVec, vec)
//      scoreTry.get.score.toFloat
    }

  }

  override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight = new ExactSimilarityWeight(searcher)

  override def toString(field: String): String =
    s"ExactSimilarityQuery for field [$field], query vector [${nospaces(queryVec)}], similarity [${simFunc.similarity}]"

  override def equals(other: Any): Boolean = other match {
    case q: ExactSimilarityQuery[V] =>
      q.queryShardContext == queryShardContext && q.field == field && q.queryVec == queryVec && q.simFunc == simFunc
    case _ => false
  }

  override def hashCode(): Int = Objects.hashCode(field, queryVec, simFunc)

}

object ExactSimilarityQuery {

  def storedVectorField(field: String): String = s"$field.$ELASTIKNN_NAME.vector"

  def index[V <: Vec: ByteArrayCodec](field: String, vec: V): Seq[IndexableField] = {
    Seq(new BinaryDocValuesField(storedVectorField(field), new BytesRef(implicitly[ByteArrayCodec[V]].apply(vec))))
  }

}
