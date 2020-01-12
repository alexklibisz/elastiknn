package org.elasticsearch.elastiknn.query

import org.apache.lucene.index.LeafReaderContext
import org.elasticsearch.common.lucene.search.function.{CombineFunction, LeafScoreFunction, ScoreFunction}

class KnnExactScoreFunction extends ScoreFunction(CombineFunction.REPLACE) {
  override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {

    ???
  }

  override def needsScores(): Boolean = ???

  override def doEquals(other: ScoreFunction): Boolean = ???

  override def doHashCode(): Int = ???
}
