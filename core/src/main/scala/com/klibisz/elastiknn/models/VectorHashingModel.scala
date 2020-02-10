package com.klibisz.elastiknn.models

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import io.circe._
import io.circe.syntax._
import com.klibisz.elastiknn.utils.PerformanceUtils._
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions._
import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn.utils.Utils._
import com.klibisz.elastiknn.{ElastiKnnVector, ExactModelOptions, JaccardLshOptions, ProcessorOptions, SparseBoolVector, _}

import scala.util._
import scala.util.hashing.MurmurHash3

object ExactModel {
  import com.klibisz.elastiknn.ElastiKnnVector.Vector.{FloatVector, SparseBoolVector}

  def apply(popts: ProcessorOptions, mopts: ExactModelOptions, vec: ElastiKnnVector): Try[Unit] = (mopts.similarity, vec) match {
    case (SIMILARITY_ANGULAR | SIMILARITY_L1 | SIMILARITY_L2, ElastiKnnVector(FloatVector(fv), _)) =>
      if (fv.values.length == popts.dimension) Success(()) else Failure(VectorDimensionException(fv.values.length, popts.dimension))
    case (SIMILARITY_HAMMING | SIMILARITY_JACCARD, ElastiKnnVector(SparseBoolVector(sbv), _)) =>
      if (sbv.totalIndices == popts.dimension) Success(()) else Failure(VectorDimensionException(sbv.totalIndices, popts.dimension))
    case _ => Failure(SimilarityAndTypeException(mopts.similarity, vec))
  }
}

class JaccardLshModel(opts: JaccardLshOptions) {
  import VectorHashingModel._
  import opts._

  private val rng: Random = new Random(seed)

  private val coefficients: Seq[(Int, Int)] = for {
    _ <- 0 until numTables * numBands * numRows
    a = 1 + rng.nextInt(HASH_PRIME - 1)
    b = rng.nextInt(HASH_PRIME - 1)
  } yield (a, b)

  private val hashFuncs: Array[Int => Long] = coefficients.toArray.map {
    case (a, b) =>
      (i: Int) =>
        ((1L + i) * a + b) % HASH_PRIME
  }

  private val emptyHash: Map[String, String] = (for {
    ti <- 0 until numTables
    bi <- 0 until numBands
    h = MurmurHash3.orderedHash(Array.fill(numRows)(Int.MaxValue))
  } yield (s"$ti,$bi", h.toString)).toMap

  private def minHash(hashFunc: Int => Long, indices: IndexedSeq[Int]): Long = {
    var min = Long.MaxValue
    fastfor(0, _ < indices.length) { i =>
      val h = hashFunc(indices(i))
      if (h < min) min = h
    }
    min
  }

  def hash(sbv: SparseBoolVector): Map[String, String] =
    if (sbv.isEmpty) emptyHash
    else {
      // Implemented in a way that should avoid creating any ancillary data structures in the loops.
      var hh = Map.empty[String, String]
      val rh = Array.fill(numRows)(Long.MaxValue)
      var hi = 0
      fastfor(0, _ < numTables) { ti =>
        fastfor(0, _ < numBands) { bi =>
          fastfor(0, _ < numRows) { ri =>
            rh.update(ri, minHash(hashFuncs(hi), sbv.trueIndices))
            hi += 1
          }
          hh += (s"$ti,$bi" -> MurmurHash3.orderedHash(rh).toString)
        }
      }
      hh
    }

  def hash(vec: ElastiKnnVector): Try[Map[String, String]] = vec match {
    case ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv), _) => Success(hash(sbv))
    case _                                                                => Failure(SimilarityAndTypeException(SIMILARITY_JACCARD, vec))
  }
}

object VectorHashingModel {

  private[models] val HASH_PRIME: Int = 2038074743

  private val jaccardCache: LoadingCache[JaccardLshOptions, JaccardLshModel] =
    CacheBuilder.newBuilder.build(new CacheLoader[JaccardLshOptions, JaccardLshModel] {
      def load(opts: JaccardLshOptions): JaccardLshModel = new JaccardLshModel(opts)
    })

  def hash(processorOptions: ProcessorOptions, elastiKnnVector: ElastiKnnVector): Try[Map[String, String]] =
    (processorOptions.modelOptions, elastiKnnVector) match {
      case (Exact(mopts), _)   => ExactModel(processorOptions, mopts, elastiKnnVector).map(_ => Map.empty)
      case (Jaccard(mopts), _) => jaccardCache.get(mopts).hash(elastiKnnVector)
      case _                   => ???
    }

  def toJson(popts: ProcessorOptions, vec: ElastiKnnVector): Try[Json] = hash(popts, vec).map(_.asJson)

}
