package org.apache.lucene.search

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.LshFunction
import com.klibisz.elastiknn.query.{ExactQuery, LshFunctionCache}
import com.klibisz.elastiknn.storage.{StoredVec, UnsafeSerialization}
import org.apache.lucene.document.Field
import org.apache.lucene.index._
import org.apache.lucene.util.BytesRef
import org.elasticsearch.index.mapper.MappedFieldType

object LshQuery {

  def apply[M <: Mapping, V <: Vec, S <: StoredVec](field: String,
                                                    query: V,
                                                    candidates: Int,
                                                    lshFunction: LshFunction[M, V, S],
                                                    indexReader: IndexReader)(implicit codec: StoredVec.Codec[V, S]): Query = {

    val scoreFunction = (lrc: LeafReaderContext) => {
      val binaryDocValues = lrc.reader.getBinaryDocValues(ExactQuery.vectorDocValuesField(field))
      (docId: Int) =>
        if (binaryDocValues.advanceExact(docId)) {
          val bref = binaryDocValues.binaryValue()
          val storedVec = codec.decode(bref.bytes, bref.offset, bref.length)
          lshFunction.exact(query, storedVec)
        } else throw new RuntimeException(s"Couldn't advance to doc with id [$docId]")
    }

    val terms = lshFunction(query).map(h => new BytesRef(UnsafeSerialization.writeInt(h)))

    new OptimizedScoreFunctionQuery(
      field,
      terms,
      candidates,
      scoreFunction,
      indexReader
    )
  }

  def index[M <: Mapping, V <: Vec: StoredVec.Encoder, S <: StoredVec](
      field: String,
      fieldType: MappedFieldType,
      vec: V,
      lshFunction: LshFunction[M, V, S])(implicit lshFunctionCache: LshFunctionCache[M, V, S]): Seq[IndexableField] = {
    ExactQuery.index(field, vec) ++ lshFunction(vec).map { h =>
      new Field(field, UnsafeSerialization.writeInt(h), fieldType)
    }
  }
}
