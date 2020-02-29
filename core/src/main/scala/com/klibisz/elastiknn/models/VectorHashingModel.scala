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

class JaccardLshModel(opts: JaccardLshOptions) {
  import VectorHashingModel._
  import opts._

  private val rng: Random = new Random(seed)

  private val alphas: Array[Int] = (0 until numBands * numRows).map(_ => 1 + rng.nextInt(HASH_PRIME - 1)).toArray
  private val betas: Array[Int] = (0 until numBands * numRows).map(_ => rng.nextInt(HASH_PRIME - 1)).toArray

  private lazy val emptyHashes: Array[Long] = Array.fill(numRows)(HASH_PRIME)

  final def hash(trueIndices: Array[Int]): Array[Long] =
    if (trueIndices.isEmpty) emptyHashes
    else {
      val tableBandHashes = new Array[Long](numBands)
      var ixTableBandHashes = 0
      var ixCoefficients = 0
      while (ixTableBandHashes < tableBandHashes.length) {
        var bandHash = 0L
        var ixRows = 0
        while (ixRows < numRows) {
          val a = alphas(ixCoefficients)
          val b = betas(ixCoefficients)
          var rowHash = Long.MaxValue
          var ixTrueIndices = 0
          while (ixTrueIndices < trueIndices.length) {
            val indexHash = ((1L + trueIndices(ixTrueIndices)) * a + b) % HASH_PRIME
            if (indexHash < rowHash) rowHash = indexHash
            ixTrueIndices += 1
          }
          bandHash = (bandHash + rowHash) % HASH_PRIME
          ixRows += 1
          ixCoefficients += 1
        }
        tableBandHashes.update(ixTableBandHashes, ((ixTableBandHashes % HASH_PRIME) + bandHash) % HASH_PRIME)
        ixTableBandHashes += 1
      }
      tableBandHashes
    }

}

object VectorHashingModel {

  private[models] val HASH_PRIME: Int = 2038074743

  private val jaccardCache: LoadingCache[JaccardLshOptions, JaccardLshModel] =
    CacheBuilder.newBuilder.build(new CacheLoader[JaccardLshOptions, JaccardLshModel] {
      def load(opts: JaccardLshOptions): JaccardLshModel = new JaccardLshModel(opts)
    })

  def hash(processorOptions: ProcessorOptions, elastiKnnVector: ElastiKnnVector): Try[String] =
    (processorOptions.modelOptions, elastiKnnVector) match {
      case (Exact(mopts), _) => ExactModel(processorOptions, mopts, elastiKnnVector).map(_ => "")
      case (Jaccard(opts), ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv))) =>
        Try(jaccardCache.get(opts).hash(sbv.trueIndices).mkString(" "))
      case other => Failure(new NotImplementedError(s"Hashing is not implemented for $other"))
    }

}

object Profile {
  def main(args: Array[String]): Unit = {

    implicit val r = new Random(100)
    val m = new JaccardLshModel(JaccardLshOptions(0, "", 150, 1))

    val vecs = SparseBoolVector.randoms(100, 5000)

    println(m.hash(vecs.head.trueIndices).hashCode())

    while (true) {
      val t0 = System.currentTimeMillis()
      vecs.foreach(v => m.hash(v.trueIndices))
      println(vecs.length / (System.currentTimeMillis() - t0) * 1000)
    }

  }
}
