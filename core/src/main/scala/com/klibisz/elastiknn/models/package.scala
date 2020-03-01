package com.klibisz.elastiknn

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions.{ExactComputed, ExactIndexed, JaccardLsh}
import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn.ElastiKnnVector.Vector

import scala.util.{Failure, Success, Try}

package object models {

  private[models] val HASH_PRIME: Int = 2038074743

  private lazy val jaccardCache: LoadingCache[(Long, Int, Int), JaccardLshModel] =
    CacheBuilder.newBuilder.build(new CacheLoader[(Long, Int, Int), JaccardLshModel] {
      def load(opts: (Long, Int, Int)): JaccardLshModel = new JaccardLshModel(opts._1, opts._2, opts._3)
    })

  def toDocValue(popts: ProcessorOptions, ekv: ElastiKnnVector): Try[String] =
    (popts.modelOptions, ekv) match {
      case (ExactComputed(mopts), _) => {
        (mopts.similarity, ekv) match {
          case (SIMILARITY_ANGULAR | SIMILARITY_L1 | SIMILARITY_L2, ElastiKnnVector(Vector.FloatVector(fv))) =>
            if (fv.values.length == popts.dimension) Success(()) else Failure(VectorDimensionException(fv.values.length, popts.dimension))
          case (SIMILARITY_HAMMING | SIMILARITY_JACCARD, ElastiKnnVector(Vector.SparseBoolVector(sbv))) =>
            if (sbv.totalIndices == popts.dimension) Success(()) else Failure(VectorDimensionException(sbv.totalIndices, popts.dimension))
          case _ => Failure(SimilarityAndTypeException(mopts.similarity, ekv))
        }
      }.map(_ => "")
      case (ExactIndexed(ExactIndexedOptions(SIMILARITY_JACCARD, _)), ElastiKnnVector(Vector.SparseBoolVector(sbv))) =>
        Try(sbv.trueIndices.mkString(" "))
      case (JaccardLsh(opts), ElastiKnnVector(Vector.SparseBoolVector(sbv))) =>
        Try(jaccardCache.get((opts.seed, opts.numBands, opts.numRows)).hash(sbv.trueIndices).mkString(" "))
      case other => Failure(new NotImplementedError(s"Hashing is not implemented for $other"))
    }

}
