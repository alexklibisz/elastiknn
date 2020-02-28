package com.klibisz.elastiknn.models

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions._
import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn.utils.Utils._
import com.klibisz.elastiknn.{ElastiKnnVector, ExactModelOptions, JaccardLshOptions, ProcessorOptions, _}

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

class JaccardLshModelScala(opts: JaccardLshOptions) {
  import VectorHashingModel._
  import opts._

  private val rng: Random = new Random(seed)

  private val alphas: Array[Int] = (0 until numTables * numBands * numRows).toArray.map(_ => 1 + rng.nextInt(HASH_PRIME - 1))
  private val betas: Array[Int] = (0 until numTables * numBands * numRows).toArray.map(_ => rng.nextInt(HASH_PRIME - 1))

  private def tableBandHash(table: Int, band: Int, bandHash: Long): Long =
    ((((table % HASH_PRIME) + band) % HASH_PRIME) + bandHash) % HASH_PRIME

  private lazy val emptyHashes: Array[Long] = {
    var bandHash = 0L
    fastfor(0, _ < numRows) { _ =>
      bandHash = (bandHash + Long.MaxValue) % HASH_PRIME
    }
    (for {
      t <- 0 until numTables
      b <- 0 until numBands
    } yield tableBandHash(t, b, bandHash)).toArray
  }

  final def hash(trueIndices: Array[Int]): Array[Long] =
    if (trueIndices.isEmpty) emptyHashes
    else {
      val tableBandHashes = new Array[Long](numTables * numBands)
      var (ixHashes, ixCoefs) = (0, 0)
      fastfor(0, _ < numTables) { t =>
        fastfor(0, _ < numBands) { b =>
          var bandHash = 0L
          fastfor(0, _ < numRows) { _ =>
            var rowHash = Long.MaxValue
            val (a, b) = (alphas(ixCoefs), betas(ixCoefs))
            fastfor(0, _ < trueIndices.length) { i =>
              rowHash = rowHash.min(((1L + trueIndices(i)) * a + b) % HASH_PRIME)
            }
            bandHash = (bandHash + rowHash) % HASH_PRIME
            ixCoefs += 1
          }
          tableBandHashes.update(ixHashes, tableBandHash(t, b, bandHash))
          ixHashes += 1
        }
      }
      tableBandHashes
    }

}

object VectorHashingModel {

  private[models] val HASH_PRIME: Int = 2038074743

  private val jaccardCache: LoadingCache[JaccardLshOptions, JaccardLshModelScala] =
    CacheBuilder.newBuilder.build(new CacheLoader[JaccardLshOptions, JaccardLshModelScala] {
      def load(opts: JaccardLshOptions): JaccardLshModelScala = new JaccardLshModelScala(opts)
    })

  def hash(processorOptions: ProcessorOptions, elastiKnnVector: ElastiKnnVector): Try[String] =
    (processorOptions.modelOptions, elastiKnnVector) match {
      case (Exact(mopts), _) => ExactModel(processorOptions, mopts, elastiKnnVector).map(_ => "")
      case (Jaccard(opts), ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv))) =>
        Try(jaccardCache.get(opts).hash(sbv.trueIndices).mkString(" "))
      case other => Failure(new NotImplementedError(s"Hashing is not implemented for $other"))
    }

}
