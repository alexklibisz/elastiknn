package com.klibisz.elastiknn.query

import org.apache.lucene.index.IndexReader
import org.apache.lucene.search.Query
import org.elasticsearch.common.lucene.search.function.ScoreFunction

/** Represents an abstract query that can be turned into a lucene query and scoring function.
  */
trait ElastiknnQuery {
  def toLuceneQuery(indexReader: IndexReader): Query
  def toScoreFunction(indexReader: IndexReader): ScoreFunction
}
