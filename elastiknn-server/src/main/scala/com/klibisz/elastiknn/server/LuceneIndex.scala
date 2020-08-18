package com.klibisz.elastiknn.server

import com.klibisz.elastiknn.api.Mapping
import org.apache.lucene.index.IndexWriter

sealed trait LuceneIndex {
  def name: String
}

object LuceneIndex {

  case class Empty(name: String) extends LuceneIndex {}

  case class Open(name: String, mapping: Mapping, indexWriter: IndexWriter) extends LuceneIndex

  case class Closed(name: String) extends LuceneIndex {}

}
