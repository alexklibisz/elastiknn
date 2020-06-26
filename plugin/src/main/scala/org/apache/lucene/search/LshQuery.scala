package org.apache.lucene.search

import java.util

import com.carrotsearch.hppc.IntIntScatterMap
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.LshFunction
import com.klibisz.elastiknn.query.{ExactQuery, LshFunctionCache}
import com.klibisz.elastiknn.storage.{StoredVec, UnsafeSerialization}
import com.klibisz.elastiknn.utils.ArrayUtils
import org.apache.logging.log4j.LogManager
import org.apache.lucene.document.Field
import org.apache.lucene.index._
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.Weight.DefaultBulkScorer
import org.apache.lucene.util.{ArrayUtil, BytesRef}
import org.elasticsearch.index.mapper.MappedFieldType

import scala.collection.mutable.ArrayBuffer

/**
  * Custom query optimized for efficiently finding a set of candidate vectors that share hashes with the query vector,
  * then computing exact similarity on those vectors. Much of the implementation is based on the [[TermInSetQuery]].
  * @param field
  * @param query
  * @param candidates
  * @param lshFunc
  * @param reader
  * @param codec
  * @tparam M
  * @tparam V
  * @tparam S
  */
class LshQuery[M <: Mapping, V <: Vec, S <: StoredVec](val field: String,
                                                       val query: V,
                                                       val candidates: Int,
                                                       val lshFunc: LshFunction[M, V, S],
                                                       val reader: IndexReader)(implicit codec: StoredVec.Codec[V, S])
    extends Query {

  private val queryHashes = lshFunc(query)

  private val logger = LogManager.getLogger(this.getClass)

  private val sortedTerms: PrefixCodedTerms = {
    val byteRefs = queryHashes.distinct.map(h => new BytesRef(UnsafeSerialization.writeInt(h)))
    ArrayUtil.timSort(byteRefs)
    val b = new PrefixCodedTerms.Builder()
    byteRefs.foreach(t => b.add(field, t))
    b.finish()
  }
  private val sortedTermsHashCode: Int = sortedTerms.hashCode()

  // Expected size for the doc id -> term count mapping. Limit at 1mb of (int, int) pairs.
  // If this is too small, the IntIntScatterMap will spend a noticeable amount of time re-sizing itself.
  private val expectedMatchingDocs: Int = reader.getDocCount(field).min(125000)

  // The original [[TermInSetQuery]] deals with a case where there might be < 16 terms.
  // In that case it rewrites to a BooleanQuery. We can safely ignore that.
  // TODO: should this re-instantiate with the given reader?
  override def rewrite(reader: IndexReader): Query = super.rewrite(reader)

  override def visit(visitor: QueryVisitor): Unit =
    if (visitor.acceptField(field)) {
      val qv = visitor.getSubVisitor(Occur.SHOULD, this)
      val termIterator = sortedTerms.iterator()
      val terms = ArrayBuffer.empty[Term]
      var term = termIterator.next()
      while (term != null) {
        terms.append(new Term(field, BytesRef.deepCopyOf(term)))
        term = termIterator.next()
      }
      qv.consumeTerms(this, terms: _*)
    } else ()

  override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight = {
    new Weight(this) {
      override def extractTerms(terms: util.Set[Term]): Unit = ()

      override def matches(context: LeafReaderContext, doc: Int): Matches =
        MatchesUtils.forField(
          field,
          () => DisjunctionMatchesIterator.fromTermsEnum(context, doc, getQuery, field, sortedTerms.iterator())
        )

      private def buildIterator(leafReader: LeafReader): DocIdSetIterator = {
        val terms = leafReader.terms(field)
        val termsEnum: TermsEnum = terms.iterator()
        var docs: PostingsEnum = null
        val iterator = sortedTerms.iterator()
        val docIdToTermCount = new IntIntScatterMap(expectedMatchingDocs)
        var term = iterator.next()
        while (term != null) {
          if (termsEnum.seekExact(term)) {
            docs = termsEnum.postings(docs, PostingsEnum.NONE)
            var i = 0
            while (i < docs.cost()) {
              val docId = docs.nextDoc()
              docIdToTermCount.putOrAdd(docId, 0, 1)
              i += 1
            }
          }
          term = iterator.next()
        }

        val docIds: Array[Int] = if (docIdToTermCount.size() <= candidates) {
          // Use all the doc ids.
          docIdToTermCount.keys().toArray
        } else {
          // Build up an array of doc ids with term counts >= to the _candidates_ largest count, preferring > over >=.
          val minCandidateTermCount = ArrayUtils.quickSelectCopy(docIdToTermCount.values, candidates)
          val docIdsGt = new ArrayBuffer[Int](candidates)
          val docIdsEq = new ArrayBuffer[Int](candidates)
          var i = 0
          while (i < docIdToTermCount.keys.length && docIdsGt.length < candidates) {
            val docId = docIdToTermCount.keys(i)
            if (docIdToTermCount.containsKey(docId)) {
              val count = docIdToTermCount.get(docId)
              if (count > minCandidateTermCount) docIdsGt.append(docId)
              else if (count == minCandidateTermCount) docIdsEq.append(docId)
            }
            i += 1
          }
          if (docIdsGt.length < candidates) {
            docIdsGt.appendAll(docIdsEq.take(candidates - docIdsGt.length))
          }
          docIdsGt.toArray
        }

        new LshQuery.GivenDocIdsIterator(docIds)

      }

      private def scorer(leafReader: LeafReader): Scorer = {
        new Scorer(this) {

          private val vecDocVals = leafReader.getBinaryDocValues(ExactQuery.vectorDocValuesField(field))
          private val disi = buildIterator(leafReader)

          override def iterator(): DocIdSetIterator = disi

          override def getMaxScore(upTo: Int): Float = lshFunc.exact.maxScore

          override def score(): Float = {
            // TODO: consider caching now that this read takes about ~20% of runtime on benchmark.
            val docId = disi.docID()
            if (vecDocVals.advanceExact(docId)) {
              val binVal = vecDocVals.binaryValue()
              val storedVec = codec.decode(binVal.bytes, binVal.offset, binVal.length)
              lshFunc.exact(query, storedVec).toFloat
            } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")
          }

          override def docID(): Int = disi.docID()
        }

      }

      override def bulkScorer(context: LeafReaderContext): BulkScorer = new DefaultBulkScorer(scorer(context.reader))

      override def scorer(context: LeafReaderContext): Scorer = scorer(context.reader())

      override def explain(context: LeafReaderContext, doc: Int): Explanation = ???

      override def isCacheable(ctx: LeafReaderContext): Boolean = true // TODO: is it?
    }
  }

  override def toString(field: String): String = s"${this.getClass.getSimpleName} for field [$field] with [${queryHashes.length}] hashes"

  override def equals(obj: Any): Boolean = obj match {
    case other: LshQuery[M, V, S] =>
      this.field == other.field && this.query == other.query && this.candidates == other.candidates && this.lshFunc == other.lshFunc && this.reader == other.reader
    case _ => false
  }

  override def hashCode(): Int = 31 * classHash() + sortedTermsHashCode

}

object LshQuery {

  /** DocIdSetIterator constructed from an Array of doc ids. I'm surprised this doesn't exist already. */
  private class GivenDocIdsIterator(docIds: Array[Int]) extends DocIdSetIterator {

    private var i = 0
    util.Arrays.sort(docIds)

    override def docID(): Int = docIds(i)

    override def nextDoc(): Int =
      if (i == docIds.length - 1) DocIdSetIterator.NO_MORE_DOCS
      else {
        i += 1
        docIds(i)
      }

    override def advance(target: Int): Int =
      if (target < docIds.head) docIds.head
      else if (target > docIds.last) DocIdSetIterator.NO_MORE_DOCS
      else {
        while (docIds(i) < target) i += 1
        docIds(i)
      }

    override def cost(): Long = docIds.length
  }

  def index[M <: Mapping, V <: Vec: StoredVec.Encoder, S <: StoredVec](field: String, fieldType: MappedFieldType, vec: V, mapping: M)(
      implicit lshFunctionCache: LshFunctionCache[M, V, S]): Seq[IndexableField] = {
    ExactQuery.index(field, vec) ++ lshFunctionCache(mapping)(vec).map { h =>
      new Field(field, UnsafeSerialization.writeInt(h), fieldType)
    }
  }
}
