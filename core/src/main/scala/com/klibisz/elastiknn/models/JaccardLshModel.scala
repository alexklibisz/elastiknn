package com.klibisz.elastiknn.models

import scala.util.Random

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
  * @param seed Seed for the random number generator used to generate the hash functions.
  * @param numBands The number of LSH bands. See Mining Massive Datasets, Chapter 3 for precise description.
  * @param numRows The number of rows in each LSH band. Again, see Mining Massive Datasets, Chapter.
  */
final private[models] class JaccardLshModel(seed: Long, numBands: Int, numRows: Int) {

  import VectorHashingModel.HASH_PRIME

  private val rng: Random = new Random(seed)
  private val alphas: Array[Int] = (0 until numBands * numRows).map(_ => 1 + rng.nextInt(HASH_PRIME - 1)).toArray
  private val betas: Array[Int] = (0 until numBands * numRows).map(_ => rng.nextInt(HASH_PRIME - 1)).toArray

  private lazy val emptyHashes: Array[Long] = Array.fill(numRows)(HASH_PRIME)

  /**
    * Hash the given vector using the hash functions constructed as a function of the random seed.
    *
    * @param trueIndices The list of "true" (i.e. positive) indices from a boolean vector.
    * @return An array of longs with length `numBands`.
    */
  def hash(trueIndices: Array[Int]): Array[Long] =
    if (trueIndices.isEmpty) emptyHashes
    else {
      val bandHashes = new Array[Long](numBands)
      var ixBandHashes = 0
      var ixCoefficients = 0
      while (ixBandHashes < bandHashes.length) {
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
        bandHashes.update(ixBandHashes, ((ixBandHashes % HASH_PRIME) + bandHash) % HASH_PRIME)
        ixBandHashes += 1
      }
      bandHashes
    }

}
