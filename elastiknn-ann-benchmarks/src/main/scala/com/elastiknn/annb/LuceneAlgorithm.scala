package com.elastiknn.annb

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.HashingModel
import com.klibisz.elastiknn.storage.UnsafeSerialization
import org.apache.lucene.document.{Field, FieldType, StoredField}
import org.apache.lucene.index.{IndexOptions, IndexableField}

trait LuceneAlgorithm[V <: Vec.KnownDims] {

  def toDocument(id: Long, vec: V): java.lang.Iterable[IndexableField]

}

object LuceneAlgorithm {

  private val idFieldName = "id"
  private val vecFieldName = "v"

  final class ElastiknnDenseFloatHashing(m: HashingModel.DenseFloat) extends LuceneAlgorithm[Vec.DenseFloat] {

    private val idFieldType = new FieldType()
    idFieldType.setStored(true)

    private val vecFieldType = new FieldType()
    vecFieldType.setStored(false)
    vecFieldType.setOmitNorms(true)
    vecFieldType.setIndexOptions(IndexOptions.DOCS)
    vecFieldType.setTokenized(false)
    vecFieldType.setStoreTermVectors(false)

    override def toDocument(id: Long, vec: Vec.DenseFloat): java.lang.Iterable[IndexableField] = {
      val hashes = m.hash(vec.values)
      // TODO: compare perf of ArrayList vs. LinkedList.
      val fields = new java.util.ArrayList[IndexableField](hashes.length + 2)
      fields.add(new Field(idFieldName, s"v$id", idFieldType))
      fields.add(new StoredField(vecFieldName, UnsafeSerialization.writeFloats(vec.values)))
      hashes.foreach(hf => fields.add(new Field(vecFieldName, hf.hash, vecFieldType)))
      fields
    }
  }
}
