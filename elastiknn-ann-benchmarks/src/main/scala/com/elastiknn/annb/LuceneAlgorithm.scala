package com.elastiknn.annb

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.HashingModel
import org.apache.lucene.document.{Field, FieldType}
import org.apache.lucene.index.{IndexOptions, IndexableField}

/**
  * A LuceneAlgorithm turns a vector and its ID into a set of Lucene fields.
  */
trait LuceneAlgorithm[V <: Vec.KnownDims] {

  def toDocument(id: Long, vec: V): java.lang.Iterable[IndexableField]

}

object LuceneAlgorithm {

  private val idFieldName = "id"
  private val vecFieldName = "v"

  private val idFieldType = new FieldType()
  idFieldType.setStored(true)

  private val vecFieldType = new FieldType()
  vecFieldType.setStored(false)
  vecFieldType.setOmitNorms(true)
  vecFieldType.setIndexOptions(IndexOptions.DOCS)
  vecFieldType.setTokenized(false)
  vecFieldType.setStoreTermVectors(false)

  final class ElastiknnDenseFloatHashing(m: HashingModel.DenseFloat) extends LuceneAlgorithm[Vec.DenseFloat] {
    override def toDocument(id: Long, vec: Vec.DenseFloat): java.lang.Iterable[IndexableField] = {
      val hashes = m.hash(vec.values)
      // TODO: compare perf of ArrayList vs. LinkedList.
      val fields = new java.util.ArrayList[IndexableField](hashes.length + 1)
      fields.add(new Field(idFieldName, s"v${id}", idFieldType))
      hashes.foreach(hf => fields.add(new Field(vecFieldName, hf.hash, vecFieldType)))
      fields
    }
  }
}
