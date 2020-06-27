package org.apache.lucene.search

import java.util
import java.util.Objects

import com.carrotsearch.hppc.IntIntScatterMap
import com.klibisz.elastiknn.utils.ArrayUtils
import org.apache.lucene.index._
import org.apache.lucene.util.{ArrayUtil, BytesRef}

import scala.collection.mutable.ArrayBuffer

/**
  * Custom query optimized to find the set of candidate documents that most frequently contain the given terms.
  * Then scores the the top `candidates` candidates using the given ScoreFunction.
  * Inspired by the TermInSetQuery.
  * @param termsField Field containing tokens.
  * @param terms Set of tokens, serialized to Bytesrefs.
  * @param candidates Number of candidates to re-score.
  * @param scoreFunction Fn taking a LeafReaderContext, returns a fn taking doc id and number of matched terms, returns the final score.
  * @param indexReader IndexReader used to get some stats about the tokens field.
  */
class MatchTermsAndScoreQuery[T](val termsField: String,
                                 val terms: Array[BytesRef],
                                 val candidates: Int,
                                 val scoreFunction: LeafReaderContext => (Int, Int) => Double,
                                 val indexReader: IndexReader)
    extends Query {

  private val sortedTerms: PrefixCodedTerms = {
    val builder = new PrefixCodedTerms.Builder()
    ArrayUtil.timSort(terms)
    // If the .add method call fails, it's likely cause by having duplicate bytesrefs.
    terms.foreach(t => builder.add(termsField, t))
    builder.finish()
  }

  // Expected size for the doc id -> term count mapping. Limit at 1mb of (int, int) pairs.
  // If this is too small, the IntIntScatterMap will spend a noticeable amount of time re-sizing itself.
  private val expectedMatchingDocs: Int = indexReader.getDocCount(termsField).min(125000)

  override def createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight = new Weight(this) {

    override def extractTerms(terms: util.Set[Term]): Unit = ()

    override def explain(context: LeafReaderContext, doc: Int): Explanation =
      Explanation.`match`(100, s"${this.getClass.getName}")

    override def scorer(context: LeafReaderContext): Scorer = {

      def getDocIdToMatchingCount(): IntIntScatterMap = {
        val reader = context.reader()
        val terms = reader.terms(termsField)
        val termsEnum: TermsEnum = terms.iterator()
        var docs: PostingsEnum = null
        val iterator = sortedTerms.iterator()
        val docIdToMatchingCount = new IntIntScatterMap(expectedMatchingDocs)
        var term = iterator.next()
        while (term != null) {
          if (termsEnum.seekExact(term)) {
            docs = termsEnum.postings(docs, PostingsEnum.NONE)
            var i = 0
            while (i < docs.cost()) {
              val docId = docs.nextDoc()
              docIdToMatchingCount.putOrAdd(docId, 1, 1)
              i += 1
            }
          }
          term = iterator.next()
        }
        docIdToMatchingCount
      }

      def getCandidateDocs(docIdToMatchingCount: IntIntScatterMap): Array[Int] =
        // Use all the doc ids.
        if (docIdToMatchingCount.size() <= candidates) docIdToMatchingCount.keys().toArray
        // Build an array of doc ids with term counts >= to the _candidates_ largest count, preferring > over =.
        else {
          val minCandidateTermCount = ArrayUtils.quickSelectCopy(docIdToMatchingCount.values, candidates)
          val docIdsGt = new ArrayBuffer[Int](candidates)
          val docIdsEq = new ArrayBuffer[Int](candidates)
          var i = 0
          while (i < docIdToMatchingCount.keys.length && docIdsGt.length < candidates) {
            val docId = docIdToMatchingCount.keys(i)
            if (docIdToMatchingCount.containsKey(docId)) {
              val count = docIdToMatchingCount.get(docId)
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

      val docidToMatchingCount = getDocIdToMatchingCount()
      val candidateDocs = getCandidateDocs(docidToMatchingCount)
      val disi = new MatchTermsAndScoreQuery.DocIdsArrayIterator(candidateDocs)
      val leafScoreFunction = scoreFunction(context)

      new Scorer(this) {
        override def iterator(): DocIdSetIterator = disi
        override def getMaxScore(upTo: Int): Float = Float.MaxValue
        override def score(): Float = leafScoreFunction(docID(), docidToMatchingCount.get(docID())).toFloat
        override def docID(): Int = disi.docID()
      }
    }

    override def isCacheable(ctx: LeafReaderContext): Boolean = true
  }

  override def toString(field: String): String =
    s"${this.getClass.getSimpleName} for tokens field [$termsField] with [$candidates] candidates."

  override def equals(other: Any): Boolean = other match {
    case q: MatchTermsAndScoreQuery[T] =>
      termsField == q.termsField && candidates == q.candidates && scoreFunction == q.scoreFunction && terms
        .zip(q.terms)
        .forall { case (a, b) => a == b }
  }

  override def hashCode(): Int =
    31 * classHash() + Objects.hash(termsField, terms, candidates.asInstanceOf[AnyRef], scoreFunction)

}

object MatchTermsAndScoreQuery {

  private final class DocIdsArrayIterator(docIds: Array[Int]) extends DocIdSetIterator {
    util.Arrays.sort(docIds)
    private var i = 0
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

}
