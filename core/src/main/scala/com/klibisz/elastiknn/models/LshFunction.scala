package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Mapping, Vec}

import scala.util.Random

sealed trait LshFunction[M <: Mapping, V <: Vec] extends (V => Array[Int]) {
  val mapping: M
  val exact: ExactSimilarityFunction[V]
}

object LshFunction {

  /**
    * Locality Sensitive Hashing for Jaccard similarity using the minhashing algorithm.
    *
    * Implementation is based on several sources:
    * - The Spark MinHashLsh implementation: https://spark.apache.org/docs/2.2.3/ml-features.html#minhash-for-jaccard-distance
    * - Chapter 3 from Mining Massive Datasets (Leskovec, Rajaraman, Ullman)
    * - The tdebatty/java-LSH project on Github: https://github.com/tdebatty/java-LSH
    * - The "Minhash for dummies" blog post: http://matthewcasperson.blogspot.com/2013/11/minhash-for-dummies.html
    *
    * The `hash` method intentionally avoids higher-order constructs as a performance optimization. I used the VisualVM
    * profiler to squash hotspots until only the outer method shows up. I also tried an equivalent implementation in
    * Java and found no speedup over Scala. Once you get rid of the all the collections constructs, Scala and Java
    * perform equivalently.
    *
    * @param mapping JaccardLsh Mapping. The members are used as follows:
    *                bands: The number of LSH bands. See Mining Massive Datasets, Chapter 3 for precise description.
    *                rows: The number of rows in each LSH band. Again, see Mining Massive Datasets, Chapter.
    */
  class Jaccard(override val mapping: Mapping.JaccardLsh) extends LshFunction[Mapping.JaccardLsh, Vec.SparseBool] {

    override val exact: ExactSimilarityFunction[Vec.SparseBool] = ExactSimilarityFunction.Jaccard

    import mapping._
    private val rng: Random = new Random(0)
    private val alphas: Array[Int] = (0 until bands * rows).map(_ => 1 + rng.nextInt(HASH_PRIME - 1)).toArray
    private val betas: Array[Int] = (0 until bands * rows).map(_ => rng.nextInt(HASH_PRIME - 1)).toArray
    private lazy val emptyHashes: Array[Int] = Array.fill(rows)(HASH_PRIME)

    override def apply(v: Vec.SparseBool): Array[Int] =
      if (v.trueIndices.isEmpty) emptyHashes
      else {
        val bandHashes = new Array[Int](bands)
        var ixBandHashes = 0
        var ixCoefficients = 0
        while (ixBandHashes < bandHashes.length) {
          var bandHash = 0
          var ixRows = 0
          while (ixRows < rows) {
            val a = alphas(ixCoefficients)
            val b = betas(ixCoefficients)
            var rowHash = Int.MaxValue
            var ixTrueIndices = 0
            while (ixTrueIndices < v.trueIndices.length) {
              val indexHash = ((1 + v.trueIndices(ixTrueIndices)) * a + b) % HASH_PRIME
              if (indexHash < rowHash) rowHash = indexHash // Actually faster than math.min or a.min(b).
              ixTrueIndices += 1
            }
            bandHash = (bandHash + rowHash) % HASH_PRIME
            ixRows += 1
            ixCoefficients += 1
          }
          bandHashes.update(ixBandHashes, ((ixBandHashes % HASH_PRIME) + bandHash) % HASH_PRIME)
          ixBandHashes += 1
        }
        bandHashes
      }
  }

  /**
    * Hamming Lsh model using the bit sampling technique from Chapter 3 of Mining Massive Datasets
    * (Leskovec, Rajaraman, Ullman).
    *
    * @param mapping HammingLsh Mapping. The members are used as follows:
    *                 bits: determines the number of randomly sampled indices.
    */
  class Hamming(override val mapping: Mapping.HammingLsh) extends LshFunction[Mapping.HammingLsh, Vec.SparseBool] {
    override val exact: ExactSimilarityFunction[Vec.SparseBool] = ExactSimilarityFunction.Hamming

    import mapping._
    private val rng: Random = new Random(0)
    private[models] val sampledIndices: Array[Int] = (0 until bits).map(_ => rng.nextInt(dims)).sorted.toArray

    override def apply(vec: Vec.SparseBool): Array[Int] = {
      val hashes = new Array[Int](bits)
      var (hi, ti, si) = (0, 0, 0)
      while (ti < vec.trueIndices.length && si < sampledIndices.length) {
        val s = sampledIndices(si)
        val t = vec.trueIndices(ti)
        // The true index wasn't sampled.
        if (s > t) ti += 1
        // The sampled index wasn't true.
        else if (s < t) {
          hashes.update(hi, s * 2)
          hi += 1
          si += 1
        }
        // The sampled index was true.
        else {
          hashes.update(hi, s * 2 + 1)
          hi += 1
          si += 1
          ti += 1
        }
      }
      while (si < sampledIndices.length) {
        hashes.update(hi, sampledIndices(si) * 2)
        hi += 1
        si += 1
      }
      hashes
    }
  }

}
