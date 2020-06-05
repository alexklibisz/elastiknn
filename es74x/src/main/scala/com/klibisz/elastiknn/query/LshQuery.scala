package com.klibisz.elastiknn.query

import java.lang
import java.util.Objects

import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.LshFunction
import com.klibisz.elastiknn.storage.{ByteArrayCodec, StoredVec}
import org.apache.lucene.document.{Field, FieldType}
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.lucene.search.function.{CombineFunction, FunctionScoreQuery, LeafScoreFunction, ScoreFunction}

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

      def exactScore(docId: Int): Float = {
        val storedVec = if (vecDocVals.advanceExact(docId)) {
          codec.apply(vecDocVals.binaryValue.bytes)
        } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")
        lshFunc.exact(???, storedVec).toFloat
      }

      new LeafScoreFunction {
        override def score(docId: Int, intersection: Float): Double = {
          if (candidates == 0) intersection
          else if (candsHeap.size() < candidates) {
            candsHeap.add(intersection)
            exactScore(docId)
          } else if (intersection > candsHeap.peekFirst()) {
            candsHeap.removeFirst()
            candsHeap.add(intersection)
            exactScore(docId)
          } else 0f
        }

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
      lshFunctionCache: LshFunctionCache[M, V, S])(implicit codec: StoredVec.Codec[V, S]): FunctionScoreQuery = {
    val lshFunc: LshFunction[M, V, S] = lshFunctionCache(mapping)
    val isecQuery: BooleanQuery = {
      val builder = new BooleanQuery.Builder
      lshFunc(queryVec).foreach { h =>
        val term = new Term(field, new BytesRef(ByteArrayCodec.encode(h)))
        val termQuery = new TermQuery(term)
        val clause = new BooleanClause(termQuery, BooleanClause.Occur.SHOULD)
        builder.add(clause)
      }
      builder.build()
    }
    val f = new LshScoreFunction(field, queryVec, candidates, lshFunc)
    new FunctionScoreQuery(isecQuery, f, CombineFunction.REPLACE, 0f, Float.MaxValue)
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
      new Field(field, ByteArrayCodec.encode(h), hashesFieldType)
    }
  }

}
