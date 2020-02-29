package com.klibisz.elastiknn.models

import com.google.common.cache._
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions._
import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn._

import scala.util._

object ExactModel {
  import com.klibisz.elastiknn.ElastiKnnVector.Vector.{FloatVector, SparseBoolVector}

  def apply(popts: ProcessorOptions, mopts: ExactModelOptions, vec: ElastiKnnVector): Try[Unit] = (mopts.similarity, vec) match {
    case (SIMILARITY_ANGULAR | SIMILARITY_L1 | SIMILARITY_L2, ElastiKnnVector(FloatVector(fv))) =>
      if (fv.values.length == popts.dimension) Success(()) else Failure(VectorDimensionException(fv.values.length, popts.dimension))
    case (SIMILARITY_HAMMING | SIMILARITY_JACCARD, ElastiKnnVector(SparseBoolVector(sbv))) =>
      if (sbv.totalIndices == popts.dimension) Success(()) else Failure(VectorDimensionException(sbv.totalIndices, popts.dimension))
    case _ => Failure(SimilarityAndTypeException(mopts.similarity, vec))
  }
}

object VectorHashingModel {

  private[models] val HASH_PRIME: Int = 2038074743

  private val jaccardCache: LoadingCache[(Long, Int, Int), JaccardLshModel] =
    CacheBuilder.newBuilder.build(new CacheLoader[(Long, Int, Int), JaccardLshModel] {
      def load(opts: (Long, Int, Int)): JaccardLshModel = new JaccardLshModel(opts._1, opts._2, opts._3)
    })

  def hash(processorOptions: ProcessorOptions, elastiKnnVector: ElastiKnnVector): Try[String] =
    (processorOptions.modelOptions, elastiKnnVector) match {
      case (Exact(mopts), _) => ExactModel(processorOptions, mopts, elastiKnnVector).map(_ => "")
      case (Jaccard(opts), ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv))) =>
        Try(jaccardCache.get((opts.seed, opts.numBands, opts.numRows)).hash(sbv.trueIndices).mkString(" "))
      case other => Failure(new NotImplementedError(s"Hashing is not implemented for $other"))
    }

}
