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

  /**
    * Convert the given vector into the format that will be stored in its Elasticsearch document.
    * @param popts ProcessorOptions which determine how the vector will be converted.
    * @param ekv The vector.
    * @return
    */
  def processVector(popts: ProcessorOptions, ekv: ElastiKnnVector): Try[ProcessedVector] =
    (popts.modelOptions, ekv) match {
      // Exact computed just checks that the vector's dimension matches the pre-defined dimension.
      case (ExactComputed(mopts), _) => {
        (mopts.similarity, ekv) match {
          case (SIMILARITY_ANGULAR | SIMILARITY_L1 | SIMILARITY_L2, ElastiKnnVector(Vector.FloatVector(fv))) =>
            if (fv.values.length == popts.dimension) Success(()) else Failure(VectorDimensionException(fv.values.length, popts.dimension))
          case (SIMILARITY_HAMMING | SIMILARITY_JACCARD, ElastiKnnVector(Vector.SparseBoolVector(sbv))) =>
            if (sbv.totalIndices == popts.dimension) Success(()) else Failure(VectorDimensionException(sbv.totalIndices, popts.dimension))
          case _ => Failure(SimilarityAndTypeException(mopts.similarity, ekv))
        }
      }.map(_ => ProcessedVector.ExactComputed())

      // Foobar
      case (ExactIndexed(exix), ElastiKnnVector(Vector.SparseBoolVector(sbv))) if exix.similarity == SIMILARITY_JACCARD =>
        Try(ProcessedVector.ExactIndexedJaccard(sbv.trueIndices.length, sbv.trueIndices.mkString(" ")))

      // Foobar
      case (JaccardLsh(opts), ElastiKnnVector(Vector.SparseBoolVector(sbv))) =>
        Try {
          val model = jaccardCache.get((opts.seed, opts.numBands, opts.numRows))
          val hashes = model.hash(sbv.trueIndices).mkString(" ")
          ProcessedVector.JaccardLsh(hashes)
        }
      // Foobar
      case other => Failure(new NotImplementedError(s"Hashing is not implemented for $other"))
    }

}
