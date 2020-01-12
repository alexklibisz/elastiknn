package org.elasticsearch.elastiknn.mapper

import org.elasticsearch.script.ScoreScript

/*
GET /test-similarity_jaccard-10/_search
{
    "query" : {
        "script_score" : {
            "query" : {
                "match": {
                  "_id": "c6"
                }
            },
            "script" : {
                "source" : """
return elastiKnnJaccard(doc[params.field], params.bTrueIndices);
                """,
                "params": {
                  "field": "vec_raw",
                  "bTrueIndices": {
                    "3": null, "6": null, "7": null
                  }
                }
            }
        }
     }
}
 */

class PainlessJaccard(scoreScript: ScoreScript, indexedVector: ElastiKnnVectorScriptDocValues, queryVector: java.util.Map[String, Any]) {

  def elastiKnnJaccard(): Double = 99d

}
