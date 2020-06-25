package org.apache.lucene.search

import java.util

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.LshFunction
import com.klibisz.elastiknn.query.{ExactQuery, LshFunctionCache}
import com.klibisz.elastiknn.storage.{StoredVec, UnsafeSerialization}
import org.apache.lucene.document.Field
import org.apache.lucene.index._
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

  private val termData: PrefixCodedTerms = {
    val byteRefs = queryHashes.distinct.map(h => new BytesRef(UnsafeSerialization.writeInt(h)))
    ArrayUtil.timSort(byteRefs)
    val b = new PrefixCodedTerms.Builder()
    byteRefs.foreach(t => b.add(field, t))
    b.finish()
  }
  private val termDataHashCode: Int = termData.hashCode()

  // The original [[TermInSetQuery]] deals with a case where there might be < 16 terms.
  // In that case it rewrites to a BooleanQuery. We can safely ignore that.
  // TODO: should this re-instantiate with the given reader?
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

      private def docIdSet(leafReader: LeafReader): DocIdSet = {
        val terms = leafReader.terms(field)
        val termsEnum = terms.iterator()
        val iterator = termData.iterator()
        val builder: DocIdSetBuilder = new DocIdSetBuilder(leafReader.maxDoc(), terms)
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

      private def scorer(docIdSet: DocIdSet, leafReader: LeafReader): Scorer = {
        val disi = docIdSet.iterator()
        if (disi == null) null
        // else new ConstantScoreScorer(this, boost, scoreMode, disi)
        else
          new Scorer(this) {
            private val vecDocVals = leafReader.getBinaryDocValues(ExactQuery.vectorDocValuesField(field))
            private val maxScore: Float = lshFunc match {
              case _: LshFunction.Angular => 2f
              case _                      => 1f
            }
            override def iterator(): DocIdSetIterator = disi
            override def getMaxScore(upTo: Int): Float = maxScore
            override def score(): Float = {
              val did = disi.docID()
              // val termVector = leafReader.getTermVector(did, field)
              if (vecDocVals.advanceExact(did)) {
                val binVal = vecDocVals.binaryValue()
                val storedVec = codec.decode(binVal.bytes, binVal.offset, binVal.length)
                lshFunc.exact(query, storedVec).toFloat
              } else throw new RuntimeException(s"Couldn't advance to doc with id [$did]")
            }
            override def docID(): Int = disi.docID()
          }

      }

      override def bulkScorer(context: LeafReaderContext): BulkScorer = {
        val docIdSet = this.docIdSet(context.reader())
        if (docIdSet == null) null
        else new DefaultBulkScorer(scorer(docIdSet, context.reader))
      }

      override def scorer(context: LeafReaderContext): Scorer = {
        val docIdset = this.docIdSet(context.reader())
        if (docIdset == null) null
        else this.scorer(docIdset, context.reader())
      }

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

  override def hashCode(): Int = 31 * classHash() + termDataHashCode

}

object LshQuery {
  def index[M <: Mapping, V <: Vec: StoredVec.Encoder, S <: StoredVec](field: String, fieldType: MappedFieldType, vec: V, mapping: M)(
      implicit lshFunctionCache: LshFunctionCache[M, V, S]): Seq[IndexableField] = {
    ExactQuery.index(field, vec) ++ lshFunctionCache(mapping)(vec).map { h =>
      new Field(field, UnsafeSerialization.writeInt(h), fieldType)
    }
  }
}
