package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.storage.UnsafeSerialization.writeInt

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
  * Locality Sensitive Hashing for Jaccard similarity using the minhashing algorithm.
  *
  * Implementation is based on several sources:
  * - Chapter 3 from Mining Massive Datasets (MMDS) (Leskovec, Rajaraman, Ullman)
  * - The Spark MinHashLsh implementation: https://spark.apache.org/docs/2.2.3/ml-features.html#minhash-for-jaccard-distance
  * - The tdebatty/java-LSH project on Github: https://github.com/tdebatty/java-LSH
  * - The "Minhash for dummies" blog post: http://matthewcasperson.blogspot.com/2013/11/minhash-for-dummies.html
  *
  * The `hash` method intentionally avoids higher-order constructs as a performance optimization. I used the VisualVM
  * profiler to squash hotspots until only the outer method shows up. I also tried an equivalent implementation in
  * Java and found no speedup over Scala. Once you get rid of the all the collections constructs, Scala and Java
  * perform equivalently.
  *
  * @param mapping JaccardLsh Mapping. The members are used as follows:
  *                L: number of hash tables. Generally, higher L yields higher recall.
  *                k: number of hash functions combined to generate a hash for each table. Generally, higher k yields higher precision.
  */
final class JaccardLsh(override val mapping: Mapping.JaccardLsh)
    extends HashingFunction[Mapping.JaccardLsh, Vec.SparseBool, StoredVec.SparseBool] {

  import mapping._
  private val rng: Random = new Random(0)
  private val alphas: Array[Int] = (0 until L * k).map(_ => 1 + rng.nextInt(HASH_PRIME - 1)).toArray
  private val betas: Array[Int] = (0 until L * k).map(_ => rng.nextInt(HASH_PRIME - 1)).toArray
  private val emptyHashes: Array[HashAndFrequency] = Array.fill(k)(HASH_PRIME).map(writeInt).map(new HashAndFrequency(_))

  override def apply(v: Vec.SparseBool): Array[HashAndFrequency] =
    if (v.trueIndices.isEmpty) emptyHashes
    else {
      val hashes = new Array[HashAndFrequency](L)
      var ixL = 0
      var ixCoefficients = 0
      while (ixL < hashes.length) {
        val hash = ArrayBuffer[Byte](writeInt(ixL): _*)
        var ixk = 0
        while (ixk < k) {
          val a = alphas(ixCoefficients)
          val b = betas(ixCoefficients)
          var minHash = Int.MaxValue
          var ixTrueIndices = 0
          while (ixTrueIndices < v.trueIndices.length) {
            val indexHash = ((1 + v.trueIndices(ixTrueIndices)) * a + b) % HASH_PRIME
            if (indexHash < minHash) minHash = indexHash // Actually faster than math.min or a.min(b).
            ixTrueIndices += 1
          }
          hash.appendAll(writeInt(minHash))
          ixk += 1
          ixCoefficients += 1
        }
        hashes.update(ixL, new HashAndFrequency(hash.toArray))
        ixL += 1
      }
      hashes
    }
}
