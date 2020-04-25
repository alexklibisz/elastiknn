package com.klibisz.elastiknn.query

import java.util.Objects

import com.klibisz.elastiknn.api.{ElasticsearchCodec, Vec}
import com.klibisz.elastiknn.models.ExactSimilarityFunction
import com.klibisz.elastiknn.storage.ByteArrayCodec
import com.klibisz.elastiknn.storage.VecCache.ContextCache
import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.search.Explanation
import org.elasticsearch.common.lucene.search.function.{CombineFunction, LeafScoreFunction, ScoreFunction}

class ExactScoreFunction[V <: Vec: ByteArrayCodec: ElasticsearchCodec](val field: String,
                                                                       val queryVec: V,
                                                                       val simFunc: ExactSimilarityFunction[V],
                                                                       val ctxCache: ContextCache[V])
    extends ScoreFunction(CombineFunction.REPLACE) {

  private val vectorDocValuesField = ExactSimilarityMapping.vectorDocValuesField(field)

  override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
    val vecDocVals = ctx.reader.getBinaryDocValues(vectorDocValuesField)
    val docIdCache = ctxCache.get(ctx)
    new LeafScoreFunction {
      override def score(docId: Int, subQueryScore: Float): Double = {
        val storedVec = docIdCache.get(
          docId,
          () => {
            if (vecDocVals.advanceExact(docId)) {
              val binaryValue = vecDocVals.binaryValue()
              val vecBytes = binaryValue.bytes.take(binaryValue.length)
              implicitly[ByteArrayCodec[V]].apply(vecBytes).get
            } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")
          }
        )
        simFunc(queryVec, storedVec).toFloat
      }

      override def explainScore(docId: Int, subQueryScore: Explanation): Explanation =
        Explanation.`match`(100, "Computing exact similarity scores for a query vector against _all_ indexed vectors.")
    }

  }

  override def needsScores(): Boolean = false // TODO: maybe this should be true?

  override def doEquals(other: ScoreFunction): Boolean = other match {
    case f: ExactScoreFunction[V] => field == f.field && queryVec == f.queryVec && simFunc == f.simFunc && ctxCache == f.ctxCache
    case _                        => false
  }

  override def doHashCode(): Int = Objects.hash(field, queryVec, simFunc, ctxCache)
}

object ExactScoreFunction
