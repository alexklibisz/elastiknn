package com.klibisz.elastiknn.query

import java.util.Objects

import com.klibisz.elastiknn.ElastiknnException.ElastiknnRuntimeException
import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.ExactSimilarityFunction
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.storage.StoredVec.Decoder
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index.{IndexableField, LeafReaderContext}
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Explanation}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.lucene.search.function._

object ExactQuery {

  private class ExactScoreFunction[V <: Vec, S <: StoredVec](val field: String, val queryVec: V, val simFunc: ExactSimilarityFunction[V, S])(
      implicit codec: StoredVec.Codec[V, S])
      extends ScoreFunction(CombineFunction.REPLACE) {

    override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
      val cachedReader = new StoredVecReader[S](ctx, field)
      new LeafScoreFunction {
        override def score(docId: Int, subQueryScore: Float): Double = {
          val storedVec = cachedReader(docId)
          simFunc(queryVec, storedVec)
        }
        override def explainScore(docId: Int, subQueryScore: Explanation): Explanation =
          Explanation.`match`(100, s"Elastiknn exact query")
      }
    }

    override def needsScores(): Boolean = false

    override def doEquals(other: ScoreFunction): Boolean = other match {
      case f: ExactScoreFunction[V, S] => field == f.field && queryVec == f.queryVec && simFunc == f.simFunc
      case _                           => false
    }

    override def doHashCode(): Int = Objects.hash(field, queryVec, simFunc)
  }

  /**
    * Helper class that makes it easy to read vectors that were stored using the conventions in this class.
    */
  final class StoredVecReader[S <: StoredVec: Decoder](lrc: LeafReaderContext, field: String) {
    private val vecDocVals = lrc.reader.getBinaryDocValues(field)

    def apply(docId: Int): S =
      if (vecDocVals.advanceExact(docId)) {
        val bytesRef = vecDocVals.binaryValue()
        implicitly[StoredVec.Decoder[S]].apply(bytesRef.bytes, bytesRef.offset, bytesRef.length)
      } else throw new ElastiknnRuntimeException(s"Couldn't advance to binary doc values for doc with id [$docId]")

  }

  /**
    * Instantiate an exact query, implemented as an Elasticsearch [[FunctionScoreQuery]].
    */
  def apply[V <: Vec, S <: StoredVec](field: String, queryVec: V, simFunc: ExactSimilarityFunction[V, S])(
      implicit codec: StoredVec.Codec[V, S]): FunctionScoreQuery = {
    val subQuery = new DocValuesFieldExistsQuery(field)
    val func = new ExactScoreFunction(field, queryVec, simFunc)
    new FunctionScoreQuery(subQuery, func)
  }

  /**
    * Creates and returns a single indexable field that stores the vector contents as a [[BinaryDocValuesField]].
    */
  def index[V <: Vec: StoredVec.Encoder](field: String, vec: V): Seq[IndexableField] = {
    val storedVec = implicitly[StoredVec.Encoder[V]].apply(vec)
    Seq(new BinaryDocValuesField(field, new BytesRef(storedVec)))
  }

}
