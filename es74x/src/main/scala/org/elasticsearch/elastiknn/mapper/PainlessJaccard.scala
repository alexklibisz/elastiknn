package org.elasticsearch.elastiknn.mapper

import org.elasticsearch.script.ScoreScript

class PainlessJaccard(scoreScript: ScoreScript, indexedVector: ElastiKnnVectorScriptDocValues, queryVector: java.util.Map[String, Any]) {

  def elastiKnnJaccard(): Double = {
    val sbv = indexedVector.getSparseBoolVector
    var intersected: Double = 0
    var i = 0
    while (i < sbv.trueIndices.length) {
      if (queryVector.containsKey(sbv.trueIndices(i).toString)) intersected += 1
      i += 1
    }
    intersected / (sbv.trueIndices.length + queryVector.size() - intersected)
  }

}
