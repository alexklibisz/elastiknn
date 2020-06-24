package org.apache.lucene.search

import java.util

import com.klibisz.elastiknn.storage.UnsafeSerialization
import org.apache.lucene.index._
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.Weight.DefaultBulkScorer
import org.apache.lucene.util.{ArrayUtil, BytesRef, DocIdSetBuilder}

import scala.collection.mutable.ArrayBuffer

class HashesInSetQuery(val field: String, val hashes: Array[Int]) extends Query {

  private val termData: PrefixCodedTerms = {
    val byteRefs = hashes.distinct.map(h => new BytesRef(UnsafeSerialization.writeInt(h)))
    ArrayUtil.timSort(byteRefs)
    val b = new PrefixCodedTerms.Builder()
    byteRefs.foreach(t => b.add(field, t))
    b.finish()
  }
  private val termDataHashCode: Int = termData.hashCode()

  // The original [[TermInSetQuery]] deals with a case where there might be < 16 terms.
  // In that case it rewrites to a BooleanQuery. We can safely ignore that.
  override def rewrite(reader: IndexReader): Query = super.rewrite(reader)

  override def visit(visitor: QueryVisitor): Unit =
    if (visitor.acceptField(field)) {
      val qv = visitor.getSubVisitor(Occur.SHOULD, this)
      val termIterator = termData.iterator()
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
          () => DisjunctionMatchesIterator.fromTermsEnum(context, doc, getQuery, field, termData.iterator())
        )

      private def rewrite(context: LeafReaderContext): DocIdSet = {
        val reader = context.reader()
        val terms = reader.terms(field)
        val termsEnum = terms.iterator()
        val iterator = termData.iterator()
        val builder: DocIdSetBuilder = new DocIdSetBuilder(reader.maxDoc(), terms)
        var docs: PostingsEnum = null
        var term = iterator.next()
        while (term != null) {
          if (termsEnum.seekExact(term)) {
            docs = termsEnum.postings(docs, PostingsEnum.NONE)
            builder.add(docs)
          }
          term = iterator.next()
        }
        builder.build()
      }

      private def scorer(docIdSet: DocIdSet): Scorer = {
        if (docIdSet == null) null
        else {
          val disi = docIdSet.iterator()
          if (disi == null) null
          else new ConstantScoreScorer(this, boost, scoreMode, disi)
        }
      }

      override def bulkScorer(context: LeafReaderContext): BulkScorer = {
        val s = this.scorer(rewrite(context))
        if (s == null) null
        else new DefaultBulkScorer(s)
      }

      override def scorer(context: LeafReaderContext): Scorer = {
        this.scorer(rewrite(context))
      }

      override def explain(context: LeafReaderContext, doc: Int): Explanation = ???

      override def isCacheable(ctx: LeafReaderContext): Boolean = ???
    }
  }

  override def toString(field: String): String = s"${this.getClass.getSimpleName} for field [$field] with [${hashes.length}] hashes"

  override def equals(obj: Any): Boolean = obj match {
    case hiq: HashesInSetQuery =>
      this.field == hiq.field && this.hashes.length == hiq.hashes.length && java.util.Arrays.equals(this.hashes, hiq.hashes)
    case _ => false
  }

  override def hashCode(): Int = 31 * classHash() + termDataHashCode

}
