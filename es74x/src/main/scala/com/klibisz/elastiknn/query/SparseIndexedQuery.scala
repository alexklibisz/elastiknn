package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.storage.ByteArrayCodec
import org.apache.lucene.document.{Field, FieldType, StoredField}
import org.apache.lucene.index.{IndexOptions, IndexableField}
import org.apache.lucene.search.Query

//sealed trait SparseIndexedSupport[S <: Similarity]
//object SparseIndexedSupport {
//  case object Jaccard extends SparseIndexedSupport[]
//}

class SparseIndexedQuery(field: String, queryVec: Vec.SparseBool, sim: Similarity) extends Query {

  override def toString(field: String): String = ???
  override def equals(obj: Any): Boolean = ???
  override def hashCode(): Int = ???
}

object SparseIndexedQuery {

  def indexedIndicesField(field: String): String = s"$field.$ELASTIKNN_NAME.true_indices"
  def storedNumTrueField(field: String): String = s"$field.$ELASTIKNN_NAME.num_true"

  val indexedIndicesFieldType: FieldType = {
    val ft = new FieldType
    ft.setIndexOptions(IndexOptions.DOCS)
    ft.setTokenized(false)
    ft
  }

  def index(field: String, vec: Vec.SparseBool): Seq[IndexableField] = {
    val indexedIndicesFieldName = this.indexedIndicesField(field)
    ExactSimilarityQuery.index(field, vec) ++ vec.trueIndices.map { ti =>
      new Field(indexedIndicesFieldName, ByteArrayCodec.encode(ti), indexedIndicesFieldType)
    } :+ new StoredField(storedNumTrueField(field), vec.trueIndices.length)
  }

}
