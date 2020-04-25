package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.storage.ByteArrayCodec
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index.IndexableField
import org.apache.lucene.util.BytesRef

object ExactSimilarityMapping {

  // Docvalue fields can have a custom name, but "regular" values (e.g. Terms) must keep the name of the field.
  def vectorDocValuesField(field: String): String = s"$field.$ELASTIKNN_NAME.vector"

  def index[V <: Vec: ByteArrayCodec](field: String, vec: V): Seq[IndexableField] = {
    Seq(new BinaryDocValuesField(vectorDocValuesField(field), new BytesRef(implicitly[ByteArrayCodec[V]].apply(vec))))
  }

}
