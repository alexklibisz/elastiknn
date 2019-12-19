package com.klibisz.elastiknn.models

import com.google.common.cache.{CacheBuilder, CacheLoader}
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions._
import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.utils.Implicits._
import io.circe._
import io.circe.syntax._

import scala.annotation.tailrec
import scala.util._
import scala.util.hashing.MurmurHash3

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

class JaccardLshModel(opts: JaccardLshOptions) {
  import VectorModel._
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

  private val emptyHash: Map[String, Int] = (for {
    ti <- 0 until numTables
    bi <- 0 until numBands
    h = MurmurHash3.orderedHash(Array.fill(numRows)(Int.MaxValue))
  } yield (s"$ti,$bi", h)).toMap

  def hash(sbv: SparseBoolVector): Map[String, Int] =
    if (sbv.isEmpty) emptyHash
    else {
      // Implemented in a way that should avoid creating any ancillary data structures in the loops.
      var hh = Map.empty[String, Int]
      val rh = Array.fill(numRows)(Long.MaxValue)
      var hi = 0
      fastfor(0, _ < numTables) { ti =>
        fastfor(0, _ < numBands) { bi =>
          fastfor(0, _ < numRows) { ri =>
            val h = hashFuncs(hi)
            rh.update(ri, h(sbv.trueIndices.minBy(h)))
            hi += 1
          }
          hh += (s"$ti,$bi" -> MurmurHash3.orderedHash(rh))
        }
      }
      hh
    }

  def hash(vec: ElastiKnnVector): Try[Map[String, Int]] = vec match {
    case ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv)) => Success(hash(sbv))
    case _                                                             => Failure(SimilarityAndTypeException(SIMILARITY_JACCARD, vec))
  }
}

object VectorModel {

  private[models] val HASH_PRIME: Int = 2038074743

  private val jaccardCache = CacheBuilder.newBuilder.build(new CacheLoader[JaccardLshOptions, JaccardLshModel] {
    def load(opts: JaccardLshOptions): JaccardLshModel = new JaccardLshModel(opts)
  })

  def toJson(popts: ProcessorOptions, vec: ElastiKnnVector): Try[Json] = (popts.modelOptions, vec) match {
    case (Exact(mopts), _)   => ExactModel(popts, mopts, vec).map(_ => Json.fromJsonObject(JsonObject.empty))
    case (Jaccard(mopts), _) => jaccardCache.get(mopts).hash(vec).map(_.asJson)
    case _                   => ???
  }

  @tailrec
  private[models] def fastfor(i: Int, pred: Int => Boolean, inc: Int => Int)(f: Int => Unit): Unit =
    if (pred(i)) {
      f(i)
      fastfor(inc(i), pred, inc)(f)
    } else ()

  private[models] def fastfor(i: Int, pred: Int => Boolean)(f: Int => Unit): Unit = fastfor(i, pred, _ + 1)(f)

}
