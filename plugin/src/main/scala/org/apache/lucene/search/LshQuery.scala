package org.apache.lucene.search

import java.util

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.LshFunction
import com.klibisz.elastiknn.query.{ExactQuery, LshFunctionCache}
import com.klibisz.elastiknn.storage.{StoredVec, UnsafeSerialization}
import org.apache.lucene.document.Field
import org.apache.lucene.index._
import org.apache.lucene.search
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.Weight.DefaultBulkScorer
import org.apache.lucene.util.{ArrayUtil, BytesRef, DocIdSetBuilder}
import org.elasticsearch.index.mapper.MappedFieldType

import scala.collection.mutable.ArrayBuffer

class LshQuery[M <: Mapping, V <: Vec, S <: StoredVec](val field: String,
                                                       val query: V,
                                                       val candidates: Int,
                                                       val lshFunc: LshFunction[M, V, S],
                                                       val reader: IndexReader)(implicit codec: StoredVec.Codec[V, S])
    extends Query {

  private val queryHashes = lshFunc(query)

  private val sortedTerms: PrefixCodedTerms = {
    val byteRefs = queryHashes.distinct.map(h => new BytesRef(UnsafeSerialization.writeInt(h)))
    ArrayUtil.timSort(byteRefs)
    val b = new PrefixCodedTerms.Builder()
    byteRefs.foreach(t => b.add(field, t))
    b.finish()
  }
  private val sortedTermsHashCode: Int = sortedTerms.hashCode()

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

      private def docWithTermCountIterator(leafReader: LeafReader): DocIdSetIterator = {
        val terms = leafReader.terms(field)
        val termsEnum: TermsEnum = terms.iterator()
        var docs: PostingsEnum = null
        val iterator = sortedTerms.iterator()
        var term = iterator.next()
        val docIdToTermCount = scala.collection.mutable.Map.empty[Int, Int].withDefaultValue(0)
        while (term != null) {
          if (termsEnum.seekExact(term)) {
            docs = termsEnum.postings(docs, PostingsEnum.NONE)
            var i = 0
            while (i < docs.cost()) {
              val docId = docs.nextDoc()
              docIdToTermCount(docId) += 1
              i += 1
            }
          }
          term = iterator.next()
        }
        new LshQuery.DocIdSetWithMatchedTermsIterator(docIdToTermCount.toMap)
      }

      private def scorer(leafReader: LeafReader): Scorer = {
        val vecDocVals = leafReader.getBinaryDocValues(ExactQuery.vectorDocValuesField(field))
        val disiwtc = docWithTermCountIterator(leafReader)
        new Scorer(this) {
          override def iterator(): DocIdSetIterator = disiwtc
          override def getMaxScore(upTo: Int): Float = lshFunc.exact.maxScore
          override def score(): Float = {
            val did = disiwtc.docID()
            if (vecDocVals.advanceExact(did)) {
              val binVal = vecDocVals.binaryValue()
              val storedVec = codec.decode(binVal.bytes, binVal.offset, binVal.length)
              lshFunc.exact(query, storedVec).toFloat
            } else throw new RuntimeException(s"Couldn't advance to doc with id [$did]")
          }
          override def docID(): Int = disiwtc.docID()
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

  /** Iterator that lets you access the number of matched terms corresponding to the current doc id. */
  private class DocIdSetWithMatchedTermsIterator(docIdToTermCount: Map[Int, Int]) extends DocIdSetIterator {

    private val sortedIds = docIdToTermCount.keys.toArray.sorted
    private var i = 0

    override def docID(): Int = sortedIds(i)

    override def nextDoc(): Int =
      if (i == sortedIds.length - 1) DocIdSetIterator.NO_MORE_DOCS
      else {
        i += 1
        sortedIds(i)
      }

    override def advance(target: Int): Int =
      if (target < sortedIds.head) sortedIds.head
      else if (target > sortedIds.last) DocIdSetIterator.NO_MORE_DOCS
      else {
        while (sortedIds(i) < target) i += 1
        sortedIds(i)
      }

    override def cost(): Long = sortedIds.length

    def matchedTerms(): Int = docIdToTermCount(docID())

//    private val sortedIds = docIdToTermCount.keys.toArray.sorted
//    private var i = -1;
//
//    override def docID(): Int = if (i < 0) i else sortedIds(i)
//
//    override def nextDoc(): Int =
//      if (i == sortedIds.length - 1) DocIdSetIterator.NO_MORE_DOCS
//      else {
//        i += 1
//        sortedIds(i)
//      }
//
//    override def advance(target: Int): Int =
//      if (target < sortedIds.head) sortedIds.head
//      else if (target > sortedIds.last) DocIdSetIterator.NO_MORE_DOCS
//      else {
//        if (i < 0) i = 0
//        while (sortedIds(i) < target) i += 1
//        sortedIds(i)
//      }
//
//    override def cost(): Long = sortedIds.length
  }

  def index[M <: Mapping, V <: Vec: StoredVec.Encoder, S <: StoredVec](field: String, fieldType: MappedFieldType, vec: V, mapping: M)(
      implicit lshFunctionCache: LshFunctionCache[M, V, S]): Seq[IndexableField] = {
    ExactQuery.index(field, vec) ++ lshFunctionCache(mapping)(vec).map { h =>
      new Field(field, UnsafeSerialization.writeInt(h), fieldType)
    }
  }
}
