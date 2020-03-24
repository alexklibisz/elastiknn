package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.api
import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.storage
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index.IndexableField
import org.apache.lucene.util.BytesRef

object ExactSimilarityQuery {

  val FIELD_NAME: String = s"${ELASTIKNN_NAME}.vector"

  def index(sbv: api.Vector.SparseBoolVector): Seq[IndexableField] = {
    val stored = storage.SparseBoolVector(sbv.trueIndices, sbv.totalIndices)
    Seq(new BinaryDocValuesField(FIELD_NAME, new BytesRef(stored.toByteArray)))
  }

}
