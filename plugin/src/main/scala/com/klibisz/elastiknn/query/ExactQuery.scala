package com.klibisz.elastiknn.query

import java.util.Objects

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.ExactSimilarityFunction
import com.klibisz.elastiknn.storage.StoredVec
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index.{BinaryDocValues, IndexableField, LeafReaderContext}
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Explanation}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.lucene.search.function._

object ExactQuery {

  private class ExactScoreFunction[V <: Vec, S <: StoredVec](val field: String, val queryVec: V, val simFunc: ExactSimilarityFunction[V, S])(
      implicit codec: StoredVec.Codec[V, S])
      extends ScoreFunction(CombineFunction.REPLACE) {

    override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
      val vecDocVals = ctx.reader.getBinaryDocValues(vectorDocValuesField(field))
      new LeafScoreFunction {
        override def score(docId: Int, subQueryScore: Float): Double =
          if (vecDocVals.advanceExact(docId)) {
            // Important to drop the offset; otherwise it read the same vector over and over.
            val binVal = vecDocVals.binaryValue()
            val bytes = binVal.bytes.drop(binVal.offset)
            val storedVec = codec.decode(bytes)
            simFunc(queryVec, storedVec)
          } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")

        override def explainScore(docId: Int, subQueryScore: Explanation): Explanation = {
          Explanation.`match`(100, s"${this.getClass.getSimpleName} - subQueryScore = ${subQueryScore.getValue}")
        }
      }
    }

    override def needsScores(): Boolean = false

    override def doEquals(other: ScoreFunction): Boolean = other match {
      case f: ExactScoreFunction[V, S] => field == f.field && queryVec == f.queryVec && simFunc == f.simFunc
      case _                           => false
    }

    override def doHashCode(): Int = Objects.hash(field, queryVec, simFunc)
  }

  def apply[V <: Vec, S <: StoredVec](field: String, queryVec: V, simFunc: ExactSimilarityFunction[V, S])(
      implicit codec: StoredVec.Codec[V, S]): FunctionScoreQuery = {
    val subQuery = new DocValuesFieldExistsQuery(vectorDocValuesField(field))
    val func = new ExactScoreFunction(field, queryVec, simFunc)
    new FunctionScoreQuery(subQuery, func, CombineFunction.REPLACE, null, Float.MaxValue)
  }

  // Docvalue fields can have a custom name, but "regular" values (e.g. Terms) must keep the name of the field.
  def vectorDocValuesField(field: String): String = s"$field.$ELASTIKNN_NAME.vector"

  def index[V <: Vec: StoredVec.Encoder](field: String, vec: V): Seq[IndexableField] = {
    val bytes = implicitly[StoredVec.Encoder[V]].apply(vec)
    Seq(new BinaryDocValuesField(vectorDocValuesField(field), new BytesRef(bytes)))
  }

}
