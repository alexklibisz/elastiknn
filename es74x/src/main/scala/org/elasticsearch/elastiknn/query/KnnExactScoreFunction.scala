package org.elasticsearch.elastiknn.query

import java.util.Objects

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
  * @param queryVector The query vector.
  */
case class KnnExactScoreFunction(similarity: Similarity, queryVector: ElastiKnnVector, fieldData: ElastiKnnVectorFieldMapper.FieldData)
    extends ScoreFunction(CombineFunction.REPLACE) {

  override def getLeafScoreFunction(ctx: LeafReaderContext): LeafScoreFunction = {
    val atomicFieldData: ElastiKnnVectorFieldMapper.AtomicFieldData = fieldData.load(ctx)
    new LeafScoreFunction {
      override def score(docId: Int, subQueryScore: Float): Double = {
        val storedVector = atomicFieldData.getElastiKnnVector(docId).get
        val (sim, _) = ExactSimilarity(similarity, queryVector, storedVector).get
        sim
      }

      override def explainScore(docId: Int, subQueryScore: Explanation): Explanation = ???
    }
  }

  override def needsScores(): Boolean = false

  override def doEquals(other: ScoreFunction): Boolean = other match {
    case kesf: KnnExactScoreFunction => this == kesf
    case _                           => false
  }

  override def doHashCode(): Int = Objects.hash(similarity, queryVector, fieldData)
}
