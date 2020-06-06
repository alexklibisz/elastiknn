package com.klibisz.elastiknn.query

import java.util.Objects

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.ExactSimilarityFunction
import com.klibisz.elastiknn.storage.StoredVec
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
      val vecDocVals = ctx.reader.getBinaryDocValues(vectorDocValuesField(field))
      new LeafScoreFunction {
        override def score(docId: Int, subQueryScore: Float): Double = {
          vecDocVals.advance()
          val storedVec = if (vecDocVals.advanceExact(docId)) {
            val binaryValue = vecDocVals.binaryValue()
            codec.decode(binaryValue.bytes.take(binaryValue.length))
          } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")
          simFunc(queryVec, storedVec).toFloat
        }

        override def explainScore(docId: Int, subQueryScore: Explanation): Explanation =
          Explanation.`match`(100, "Computing exact similarity scores for a query vector against _all_ indexed vectors.")
      }
    }

    override def needsScores(): Boolean = false // TODO: maybe it does?

    override def doEquals(other: ScoreFunction): Boolean = other match {
      case f: ExactScoreFunction[V, S] => field == f.field && queryVec == f.queryVec && simFunc == f.simFunc
      case _                           => false
    }

    override def doHashCode(): Int = Objects.hash(field, queryVec, simFunc)
  }

  def apply[V <: Vec, S <: StoredVec](field: String, queryVec: V, simFunc: ExactSimilarityFunction[V, S])(
      implicit codec: StoredVec.Codec[V, S]): FunctionScoreQuery = {
    val subQuery = new DocValuesFieldExistsQuery(vectorDocValuesField(field))
    new FunctionScoreQuery(subQuery, new ExactScoreFunction(field, queryVec, simFunc))
  }

  // Docvalue fields can have a custom name, but "regular" values (e.g. Terms) must keep the name of the field.
  def vectorDocValuesField(field: String): String = s"$field.$ELASTIKNN_NAME.vector"

  def index[V <: Vec: StoredVec.Encoder](field: String, vec: V): Seq[IndexableField] = {
    Seq(new BinaryDocValuesField(vectorDocValuesField(field), new BytesRef(implicitly[StoredVec.Encoder[V]].apply(vec))))
  }

}
