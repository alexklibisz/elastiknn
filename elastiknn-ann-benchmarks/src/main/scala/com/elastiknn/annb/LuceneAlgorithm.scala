package com.elastiknn.annb

import com.klibisz.elastiknn.api.Vec
import org.apache.lucene.document.Document

trait LuceneAlgorithm[V <: Vec] {

  def apply(index: Int, vec: V): Document

}
