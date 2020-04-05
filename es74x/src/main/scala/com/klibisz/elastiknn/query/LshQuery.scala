package com.klibisz.elastiknn.query

import java.time.Duration
import java.util.Objects
import java.{lang, util}

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import com.klibisz.elastiknn.api.{ElasticsearchCodec, Mapping, Vec}
import com.klibisz.elastiknn.models.LshFunction
import com.klibisz.elastiknn.storage.ByteArrayCodec
import com.klibisz.elastiknn.storage.VecCache.{ContextCache, DocIdCache}
import org.apache.lucene.document.{Field, FieldType}
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.similarities.BooleanSimilarity
import org.apache.lucene.util.BytesRef

class LshQuery[M <: Mapping: ElasticsearchCodec, V <: Vec: ByteArrayCodec: ElasticsearchCodec](
    val field: String,
    val mapping: M,
    val query: V,
    val candidates: Int,
    val cache: ContextCache[V])(implicit lshFunctionCache: LshFunctionCache[M, V])
    extends Query {

  private val lshFunc: LshFunction[M, V] = lshFunctionCache(mapping)
  private val vectorDocValuesField: String = ExactSimilarityQuery.vectorDocValuesField(field)
  private val candidateHeap: MinMaxPriorityQueue[lang.Float] = MinMaxPriorityQueue.create[lang.Float]()

  private val intersectionQuery: BooleanQuery = {
    val builder = new BooleanQuery.Builder
    lshFunc(query).foreach { h =>
      val term = new Term(field, new BytesRef(ByteArrayCodec.encode(h)))
      val termQuery = new TermQuery(term)
      val clause = new BooleanClause(termQuery, BooleanClause.Occur.SHOULD)
      builder.add(clause)
    }
    builder.build()
  }

  class LshWeight(searcher: IndexSearcher) extends Weight(this) {
    searcher.setSimilarity(new BooleanSimilarity)
    private val intersectionWeight = intersectionQuery.createWeight(searcher, ScoreMode.COMPLETE, 1f)
    override def extractTerms(terms: util.Set[Term]): Unit = ()
    override def explain(context: LeafReaderContext, doc: Int): Explanation = ???
    override def isCacheable(ctx: LeafReaderContext): Boolean = false
    override def scorer(context: LeafReaderContext): Scorer = {
      val intersectionScorer = intersectionWeight.scorer(context)
      val vectorDocValues = context.reader.getBinaryDocValues(vectorDocValuesField)
      new LshScorer(this, intersectionScorer, vectorDocValues, cache.get(context))
    }
  }

  class LshScorer(weight: LshWeight, isecScorer: Scorer, vecDocValues: BinaryDocValues, docIdCache: DocIdCache[V]) extends Scorer(weight) {
    override def getMaxScore(upTo: Int): Float = Float.MaxValue
    override val iterator: DocIdSetIterator = if (isecScorer == null) DocIdSetIterator.empty() else isecScorer.iterator()
    override def docID(): Int = iterator.docID()

    private def exactScore(docId: Int): Float = {
      val storedVec = docIdCache.get(
        docId,
        () =>
          if (vecDocValues.advanceExact(docId)) {
            val binaryValue = vecDocValues.binaryValue
            val vecBytes = binaryValue.bytes.take(binaryValue.length)
            implicitly[ByteArrayCodec[V]].apply(vecBytes).get
          } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")
      )
      lshFunc.exact(query, storedVec).get.score.toFloat
    }

    override def score(): Float = {
      val intersection = isecScorer.score()
      if (candidates == 0) intersection
      else if (candidateHeap.size() < candidates) {
        candidateHeap.add(intersection)
        exactScore(this.docID())
      } else if (intersection > candidateHeap.peekFirst()) {
        candidateHeap.removeFirst()
        candidateHeap.add(intersection)
        exactScore(this.docID())
      } else 0f
    }

  }

  override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight = new LshWeight(searcher)

  override def toString(field: String): String =
    s"LshQuery for field [$field], mapping [${nospaces(mapping)}], query [${nospaces(query)}], LSH function [${lshFunc}], candidates [$candidates]"

  override def equals(other: Any): Boolean = other match {
    case q: LshQuery[M, V] =>
      q.field == field && q.mapping == mapping && q.query == query && q.lshFunc == lshFunc && q.candidates == candidates
    case _ => false
  }

  override def hashCode(): Int = Objects.hash(field, query, mapping, lshFunc, candidates.asInstanceOf[AnyRef])
}

object LshQuery {

  val jaccardCache: LoadingCache[Mapping.JaccardLsh, LshFunction.Jaccard] = CacheBuilder.newBuilder
    .expireAfterWrite(Duration.ofMinutes(1))
    .build(new CacheLoader[Mapping.JaccardLsh, LshFunction.Jaccard] {
      override def load(m: Mapping.JaccardLsh): LshFunction.Jaccard = new LshFunction.Jaccard(m)
    })

  private val hashesFieldType: FieldType = {
    val ft = new FieldType
    ft.setIndexOptions(IndexOptions.DOCS)
    ft.setTokenized(false)
    ft.freeze()
    ft
  }

  def index[M <: Mapping, V <: Vec: ByteArrayCodec](field: String, vec: V, mapping: M)(
      implicit lshFunctionCache: LshFunctionCache[M, V]): Seq[IndexableField] = {
    ExactSimilarityQuery.index(field, vec) ++ lshFunctionCache(mapping)(vec).map { h =>
      new Field(field, ByteArrayCodec.encode(h), hashesFieldType)
    }
  }

}
