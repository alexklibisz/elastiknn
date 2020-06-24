package com.klibisz.elastiknn.query

import java.lang
import java.util.Objects

import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.models.LshFunction
import com.klibisz.elastiknn.storage.{StoredVec, UnsafeSerialization}
import org.apache.lucene.document.Field
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.lucene.search.function.{CombineFunction, FunctionScoreQuery, LeafScoreFunction, ScoreFunction}
import scala.collection.JavaConverters._

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
          if (intersection > 0) exactScore(docId)
          else 0f

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

  def apply[M <: Mapping, V <: Vec, S <: StoredVec](field: String,
                                                    mapping: M,
                                                    queryVec: V,
                                                    candidates: Int,
                                                    lshFunctionCache: LshFunctionCache[M, V, S],
                                                    indexReader: IndexReader,
                                                    useMLTQuery: Boolean)(implicit codec: StoredVec.Codec[V, S]): Query = {
    val lshFunc: LshFunction[M, V, S] = lshFunctionCache(mapping)
    val hashes: Array[Int] = lshFunc(queryVec)
    val isecQuery = new HashesInSetQuery(field, hashes)
//    val byteRefs = hashes.map(UnsafeSerialization.writeInt).map(new BytesRef(_)).toSeq.asJavaCollection
//    val isecQuery = new TermInSetQuery(field, byteRefs)
//    val isecQuery: Query = if (useMLTQuery) {
//      val mlt = new MoreLikeThis(indexReader)
//      mlt.setFieldNames(Array(field))
//      mlt.setMinTermFreq(1)
//      mlt.setMaxQueryTerms(hashes.length)
//      mlt.setAnalyzer(new KeywordAnalyzer())
//      val readers = hashes.map(h => new InputStreamReader(new ByteArrayInputStream(UnsafeSerialization.writeInt(h))))
//      mlt.like(field, readers: _*)
//    } else {
//      val builder = new BooleanQuery.Builder
//      hashes.foreach { h =>
//        val term = new Term(field, new BytesRef(UnsafeSerialization.writeInt(h)))
//        val termQuery = new TermQuery(term)
//        val constQuery = new ConstantScoreQuery(termQuery)
//        builder.add(new BooleanClause(constQuery, BooleanClause.Occur.SHOULD))
//      }
//      builder.setMinimumNumberShouldMatch(1)
//      builder.build()
//    }
    val func = new LshScoreFunction(field, queryVec, candidates, lshFunc)
    new FunctionScoreQuery(isecQuery, func, CombineFunction.REPLACE, 0f, Float.MaxValue)
  }

  def index[M <: Mapping, V <: Vec: StoredVec.Encoder, S <: StoredVec](field: String, vec: V, mapping: M)(
      implicit lshFunctionCache: LshFunctionCache[M, V, S]): Seq[IndexableField] = {
    ExactQuery.index(field, vec) ++ lshFunctionCache(mapping)(vec).map { h =>
      new Field(field, UnsafeSerialization.writeInt(h), VectorMapper.simpleTokenFieldType)
    }
  }

}
