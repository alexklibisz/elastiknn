package com.klibisz.elastiknn.query

import java.lang
import java.util.Objects

import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.LshFunction
import com.klibisz.elastiknn.storage.{StoredVec, UnsafeSerialization}
import org.apache.lucene.document.{Field, FieldType}
import org.apache.lucene.index._
import org.apache.lucene.queryparser.xml.builders.MatchAllDocsQueryBuilder
import org.apache.lucene.search._
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.lucene.search.function.{CombineFunction, FunctionScoreQuery, LeafScoreFunction, ScoreFunction}
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder
import org.elasticsearch.index.query.{BoolQueryBuilder, ConstantScoreQueryBuilder, MoreLikeThisQueryBuilder, QueryBuilder, TermQueryBuilder}

object LshQuery {

  private class LshScoreFunction[M <: Mapping, V <: Vec, S <: StoredVec](
      val field: String,
      val query: V,
      val candidates: Int,
      val lshFunc: LshFunction[M, V, S])(implicit codec: StoredVec.Codec[V, S])
      extends ScoreFunction(CombineFunction.REPLACE) {

    private val candsHeap: MinMaxPriorityQueue[lang.Float] = MinMaxPriorityQueue.create()

    override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
      val vecDocVals = ctx.reader.getBinaryDocValues(ExactQuery.vectorDocValuesField(field))

      def exactScore(docId: Int): Double =
        if (vecDocVals.advanceExact(docId)) {
          val binVal = vecDocVals.binaryValue
          val storedVec = codec.decode(binVal.bytes, binVal.offset, binVal.length)
          lshFunc.exact(query, storedVec)
        } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")

      new LeafScoreFunction {
        override def score(docId: Int, intersection: Float): Double =
          if (candidates == 0) intersection
          else if (candsHeap.size() < candidates) {
            candsHeap.add(intersection)
            exactScore(docId)
          } else if (intersection > candsHeap.peekFirst()) {
            candsHeap.removeFirst()
            candsHeap.add(intersection)
            exactScore(docId)
          } else 0f

        override def explainScore(docId: Int, subQueryScore: Explanation): Explanation =
          Explanation.`match`(100, "Computing LSH similarity")
      }
    }

    override def needsScores(): Boolean = true // This is actually important in the FunctionScoreQuery internals.

    override def doEquals(other: ScoreFunction): Boolean = other match {
      case q: LshScoreFunction[M, V, S] =>
        q.field == field && q.lshFunc == lshFunc && q.query == query && q.lshFunc == lshFunc && q.candidates == candidates
      case _ => false
    }

    override def doHashCode(): Int = Objects.hash(field, query, lshFunc, lshFunc, candidates.asInstanceOf[AnyRef])
  }

  def apply[M <: Mapping, V <: Vec, S <: StoredVec](
      field: String,
      mapping: M,
      queryVec: V,
      candidates: Int,
      lshFunctionCache: LshFunctionCache[M, V, S])(implicit codec: StoredVec.Codec[V, S]): Query = {
    val lshFunc: LshFunction[M, V, S] = lshFunctionCache(mapping)
    val isecQuery: BooleanQuery = {
      val builder = new BooleanQuery.Builder
      lshFunc(queryVec).foreach { h =>
        val term = new Term(field, new BytesRef(UnsafeSerialization.writeInt(h)))
        val termQuery = new TermQuery(term)
        val constQuery = new ConstantScoreQuery(termQuery) // TODO: is this necessary?
        builder.add(new BooleanClause(constQuery, BooleanClause.Occur.SHOULD))
      }
      builder.setMinimumNumberShouldMatch(1)
      builder.build()
    }
    val func = new LshScoreFunction(field, queryVec, candidates, lshFunc)
    new FunctionScoreQuery(isecQuery, func, CombineFunction.REPLACE, 0f, Float.MaxValue)
  }

  private val hashesFieldType: FieldType = {
    val ft = new FieldType
    ft.setIndexOptions(IndexOptions.DOCS)
    ft.setTokenized(false)
    ft.freeze()
    ft
  }

  def index[M <: Mapping, V <: Vec: StoredVec.Encoder, S <: StoredVec](field: String, vec: V, mapping: M)(
      implicit lshFunctionCache: LshFunctionCache[M, V, S]): Seq[IndexableField] = {
    ExactQuery.index(field, vec) ++ lshFunctionCache(mapping)(vec).map { h =>
      new Field(field, UnsafeSerialization.writeInt(h), hashesFieldType)
    }
  }

}
