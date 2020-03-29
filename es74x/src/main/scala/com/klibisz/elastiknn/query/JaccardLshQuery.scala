package com.klibisz.elastiknn.query

import java.util.Objects
import java.{lang, util}

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.{ExactSimilarityFunction, JaccardLshModel}
import com.klibisz.elastiknn.storage.ByteArrayCodec
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import org.apache.lucene.document.{Field, FieldType}
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.similarities.BooleanSimilarity
import org.apache.lucene.util.BytesRef

class JaccardLshQuery(val field: String,
                      val queryVec: Vec.SparseBool,
                      val mapping: Mapping.JaccardLsh,
                      val candidates: Int,
                      val simFunc: ExactSimilarityFunction[Vec.SparseBool])
    extends Query {

  private val model: JaccardLshModel = JaccardLshQuery.modelCache.get(mapping)
  private val hashesTermField: String = JaccardLshQuery.hashesTermField(field)
  private val vectorDocValuesField: String = ExactSimilarityQuery.vectorDocValuesField(field)
  private val candidateHeap: MinMaxPriorityQueue[lang.Float] = MinMaxPriorityQueue.create[java.lang.Float]()

  private val intersectionQuery: BooleanQuery = {
    val builder = new BooleanQuery.Builder
    model.hash(queryVec.trueIndices).foreach { h =>
      val term = new Term(hashesTermField, new BytesRef(ByteArrayCodec.encode(h)))
      val termQuery = new TermQuery(term)
      val clause = new BooleanClause(termQuery, BooleanClause.Occur.SHOULD)
      builder.add(clause)
    }
    builder.build()
  }

  class JaccardLshWeight(searcher: IndexSearcher) extends Weight(this) {
    searcher.setSimilarity(new BooleanSimilarity)
    private val intersectionWeight = intersectionQuery.createWeight(searcher, ScoreMode.COMPLETE, 1f)
    override def extractTerms(terms: util.Set[Term]): Unit = ()
    override def explain(context: LeafReaderContext, doc: Int): Explanation = ???
    override def isCacheable(ctx: LeafReaderContext): Boolean = false
    override def scorer(context: LeafReaderContext): Scorer = {
      val intersectionScorer = intersectionWeight.scorer(context)
      val vectorDocValues = context.reader.getBinaryDocValues(vectorDocValuesField)
      new JaccardLshScorer(this, intersectionScorer, vectorDocValues)
    }
  }

  class JaccardLshScorer(weight: JaccardLshWeight, intersectionScorer: Scorer, vectorDocValues: BinaryDocValues) extends Scorer(weight) {
    override def getMaxScore(upTo: Int): Float = Float.MaxValue
    override val iterator: DocIdSetIterator = if (intersectionScorer == null) DocIdSetIterator.empty() else intersectionScorer.iterator()
    override def docID(): Int = iterator.docID()

    private def exactScore(): Float = {
      val docId = this.docID()
      if (vectorDocValues.advanceExact(docId)) {
        val binaryValue = vectorDocValues.binaryValue
        val vecBytes = binaryValue.bytes.take(binaryValue.length)
        val scoreTry = for {
          storedVec <- implicitly[ByteArrayCodec[Vec.SparseBool]].apply(vecBytes)
          simScore <- simFunc(queryVec, storedVec)
        } yield simScore.score.toFloat
        scoreTry.get
      } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")
    }

    override def score(): Float = {
      val intersection = intersectionScorer.score()
      if (candidates == 0) intersection
      else if (candidateHeap.size() < candidates) {
        candidateHeap.add(intersection)
        exactScore()
      } else if (intersection > candidateHeap.peekFirst()) {
        candidateHeap.removeFirst()
        candidateHeap.add(intersection)
        exactScore()
      } else 0f
    }

  }

  override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight = {
    new JaccardLshWeight(searcher)
  }

  override def toString(field: String): String =
    s"JaccardLshQuery for field [$field], query vector [${nospaces(queryVec)}], mapping [${nospaces(mapping)}] similarity [${simFunc.similarity}], candidates [$candidates]"

  override def equals(other: Any): Boolean = other match {
    case q: JaccardLshQuery =>
      q.field == field && q.queryVec == queryVec && q.mapping == mapping && q.simFunc == simFunc && q.candidates == candidates
    case _ => false
  }

  override def hashCode(): Int = Objects.hash(field, queryVec, mapping, simFunc, candidates.asInstanceOf[AnyRef])
}

object JaccardLshQuery {

  def hashesTermField(field: String): String = s"$field.$ELASTIKNN_NAME.hashes"

  private val modelCache: LoadingCache[Mapping.JaccardLsh, JaccardLshModel] =
    CacheBuilder.newBuilder.softValues.build(new CacheLoader[Mapping.JaccardLsh, JaccardLshModel] {
      override def load(m: Mapping.JaccardLsh): JaccardLshModel = new JaccardLshModel(0L, m.bands, m.rows)
    })

  private val hashesFieldType: FieldType = {
    val ft = new FieldType
    ft.setIndexOptions(IndexOptions.DOCS)
    ft.setTokenized(false)
    ft
  }

  def index(field: String, vec: Vec.SparseBool, mapping: Mapping.JaccardLsh): Seq[IndexableField] = {
    val hashesTermField = this.hashesTermField(field)
    val model = modelCache.get(mapping)
    ExactSimilarityQuery.index(field, vec) ++ model.hash(vec.trueIndices).map { h =>
      new Field(hashesTermField, ByteArrayCodec.encode(h), hashesFieldType)
    }
  }

}
