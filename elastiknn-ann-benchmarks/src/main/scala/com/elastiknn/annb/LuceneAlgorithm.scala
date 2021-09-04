package com.elastiknn.annb

import com.klibisz.elastiknn.api.Vec
import org.apache.lucene.index.IndexableField

trait LuceneAlgorithm[V <: Vec.KnownDims] {

  def index(id: Long, vec: V): Array[IndexableField]

}

object LuceneAlgorithm {
  def apply(dataset: Dataset, algo: Algorithm): LuceneAlgorithm[dataset.V] =
    new LuceneAlgorithm[dataset.V] {
      override def index(id: Long, vec: dataset.V): Array[IndexableField] = ???
    }
}
