package org.elasticsearch.elastiknn.query

import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.search.Explanation
import org.elasticsearch.common.lucene.search.function.{CombineFunction, LeafScoreFunction, ScoreFunction}
import org.elasticsearch.elastiknn.mapper.ElastiKnnVectorFieldMapper
import org.elasticsearch.elastiknn.models.ExactSimilarity
import org.elasticsearch.elastiknn.{ElastiKnnVector, Similarity}

/**
  * Query function which scores vectors in an index against the given ElastiKnnVector.
  * Might be useful to base this on FieldValueFactorFunction.
  * @param similarity The similarity function to use.
  * @param fieldData Object providing access to stored ElastiKnnVectors.
  * @param ekv1 The query vector.
  */
class KnnExactScoreFunction(similarity: Similarity, ekv1: ElastiKnnVector, fieldData: ElastiKnnVectorFieldMapper.FieldData)
    extends ScoreFunction(CombineFunction.REPLACE) {

  override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
    val atomicFieldData: ElastiKnnVectorFieldMapper.AtomicFieldData = fieldData.load(ctx)
    new LeafScoreFunction {
      override def score(docId: Int, subQueryScore: Float): Double = {
        val ekv2 = atomicFieldData.getElastiKnnVector(docId).get
        val (_, score) = ExactSimilarity(similarity, ekv1, ekv2).get
        score
      }

      override def explainScore(docId: Int, subQueryScore: Explanation): Explanation = ???
    }
  }

  override def needsScores(): Boolean = ???

  override def doEquals(other: ScoreFunction): Boolean = ???

  override def doHashCode(): Int = ???
}
