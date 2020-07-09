package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.storage.UnsafeSerialization.writeInt

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

sealed trait LshFunction[M <: Mapping, V <: Vec, S <: StoredVec] extends (V => Array[Array[Byte]]) {
  val mapping: M
  val exact: ExactSimilarityFunction[V, S]
}

object LshFunction {

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
  final class Jaccard(override val mapping: Mapping.JaccardLsh)
      extends LshFunction[Mapping.JaccardLsh, Vec.SparseBool, StoredVec.SparseBool] {

    override val exact: ExactSimilarityFunction[Vec.SparseBool, StoredVec.SparseBool] = ExactSimilarityFunction.Jaccard

    import mapping._
    private val rng: Random = new Random(0)
    private val alphas: Array[Int] = (0 until L * k).map(_ => 1 + rng.nextInt(HASH_PRIME - 1)).toArray
    private val betas: Array[Int] = (0 until L * k).map(_ => rng.nextInt(HASH_PRIME - 1)).toArray
    private val emptyHashes: Array[Array[Byte]] = Array.fill(k)(HASH_PRIME).map(writeInt)

    override def apply(v: Vec.SparseBool): Array[Array[Byte]] =
      if (v.trueIndices.isEmpty) emptyHashes
      else {
        val hashes = new Array[Array[Byte]](L)
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
          hashes.update(ixL, hash.toArray)
          ixL += 1
        }
        hashes
      }
  }

  /**
    * Locality sensitive hashing for hamming similarity using a modification of the index sampling technique from MMDS Chapter 3.
    * Specifically, there are L hash tables, and each table's hash function is implemented by randomly sampling and combining
    * k bits from the vector and combining them to create a hash value. The original method would just sample k bits and
    * treat each one as a hash value.
    *
    * @param mapping HammingLsh Mapping. The members are used as follows:
    *                L: number of hash tables. Generally, higher L yields higher recall.
    *                k: number of randomly sampled bits for each hash function.
    */
  final class Hamming(override val mapping: Mapping.HammingLsh)
      extends LshFunction[Mapping.HammingLsh, Vec.SparseBool, StoredVec.SparseBool] {
    override val exact: ExactSimilarityFunction[Vec.SparseBool, StoredVec.SparseBool] = ExactSimilarityFunction.Hamming

    import mapping._
    private val rng: Random = new Random(0)

    private case class Position(vecIndex: Int, hashIndexes: Array[Int]) {
      val barrZero: Array[Byte] = writeInt(vecIndex * 2)
      val barrOne: Array[Byte] = writeInt(vecIndex * 2 + 1)
    }

    // Sample L * k Positions, sorted by vecIndex for more efficient intersection in apply method.
    private val sampledPositions: Array[Position] = {
      case class Pair(vecIndex: Int, hashIndex: Int)
      @tailrec
      def sampleVecIndicesWithoutRepetition(n: Int, acc: Set[Int] = Set.empty, i: Int = rng.nextInt(dims)): Array[Int] =
        if (acc.size == n.min(dims)) acc.toArray
        else if (acc(i)) sampleVecIndicesWithoutRepetition(n, acc, rng.nextInt(dims))
        else sampleVecIndicesWithoutRepetition(n, acc + i, rng.nextInt(dims))

      // If L * k <= dims, just sample vec indices once, guaranteeing no repetition.
      val pairs: Array[Pair] = if ((L * k) <= dims) {
        sampleVecIndicesWithoutRepetition(L * k).zipWithIndex.map(Pair.tupled)
      } else {
        (0 until L).toArray.flatMap(hi => sampleVecIndicesWithoutRepetition(k).map(vi => Pair(hi, vi)))
      }

      pairs
        .groupBy(_.vecIndex)
        .mapValues(_.map(_.hashIndex))
        .map(Position.tupled)
        .toArray
        .sortBy(_.vecIndex)
    }

    override def apply(vec: Vec.SparseBool): Array[Array[Byte]] = {
      val hashBuffers = (0 until L).toArray.map { l =>
        val lbarr = writeInt(l)
        val buff = new ArrayBuffer[Byte](k * 3 + lbarr.length) // 3 because not _all_ ints will be 4 bytes.
        buff.appendAll(lbarr)
        buff
      }

      var (ixTrueIndices, ixSampledPositions) = (0, 0)
      while (ixTrueIndices < vec.trueIndices.length && ixSampledPositions < sampledPositions.length) {
        val pos = sampledPositions(ixSampledPositions)
        val trueIndex = vec.trueIndices(ixTrueIndices)
        // The true index wasn't sampled.
        if (pos.vecIndex > trueIndex) ixTrueIndices += 1
        // The sampled index wasn't true.
        else if (pos.vecIndex < trueIndex) {
          pos.hashIndexes.foreach(hi => hashBuffers(hi).appendAll(pos.barrZero))
          ixSampledPositions += 1
        }
        // The sampled index was true.
        else {
          pos.hashIndexes.foreach(hi => hashBuffers(hi).appendAll(pos.barrOne))
          ixTrueIndices += 1
        }
      }
      while (ixSampledPositions < sampledPositions.length) {
        val pos = sampledPositions(ixSampledPositions)
        pos.hashIndexes.foreach(hi => hashBuffers(hi).appendAll(pos.barrZero))
        ixSampledPositions += 1
      }
      hashBuffers.map(_.toArray)
    }
  }

  /**
    * Locality sensitive hashing for Angular similarity using random hyperplanes as described in MMDS Chapter 3.
    *
    * TODO: try using sketches as described in MMDS 3.7.3. Could make it a parameter in Mapping.AngularLsh.
    *
    * @param mapping AngularLsh Mapping. The members are used as follows:
    *                dims: sets the dimension of the hyperplanes equal to that of the vectors hashed by this model.
    *                bands: number of bands, each containing `rows` hash functions. Generally, more bands yield higher recall.
    *                rows: number of rows per band. Generally, more rows yield higher precision.
    */
  final class Angular(override val mapping: Mapping.AngularLsh)
      extends LshFunction[Mapping.AngularLsh, Vec.DenseFloat, StoredVec.DenseFloat] {
    override val exact: ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] = ExactSimilarityFunction.Angular

    import mapping._
    private implicit val rng: Random = new Random(0)
    private val hashVecs: Array[Vec.DenseFloat] = (0 until (bands * rows)).map(_ => Vec.DenseFloat.random(dims)).toArray

    override def apply(v: Vec.DenseFloat): Array[Array[Byte]] = {
      val bandHashes = new Array[Int](bands)
      var ixBandHashes = 0
      var ixHashVecs = 0
      while (ixBandHashes < bandHashes.length) {
        // The minimum hash value for each band is the index times 2 ^ rows. The integers between each minimum value
        // are used based on the rows. For example, if there are 4 rows, then the 3rd band can hash the given vector
        // to values in [3 * 2 ^ 4, 4 * 2 ^ 4).
        var bandHash = ixBandHashes * (1 << rows)
        var ixRows = 0
        while (ixRows < rows) {
          // Take the dot product of the hashing vector and the given vector. If the sign is positive, add 2 ^ r to the
          // hash value for this band. For example, if we're on the 3rd band, there are 4 rows per band, and the hash
          // vectors corresponding to the 2nd and 3rd rows yield a positive dot product, then the hash value will be:
          // 3 * 2^4 + 2^2 + 2^3 = 48 + 4 + 8 = 60.
          if (hashVecs(ixHashVecs).dot(v) > 0) bandHash += 1 << ixRows
          ixRows += 1
          ixHashVecs += 1
        }
        bandHashes.update(ixBandHashes, bandHash)
        ixBandHashes += 1
      }
      bandHashes.map(writeInt)
    }
  }

  /**
    * Locality sensitive hashing for L2 similarity based on MMDS Chapter 3.
    * Also drew some inspiration from this closed pull request: https://github.com/elastic/elasticsearch/pull/44374
    *
    * @param mapping L2Lsh Mapping. The members are used as follows:
    *                bands: number of bands, each containing `rows` hash functions. Generally, more bands yield higher recall.
    *                       Note that this often referred to as `L`, or the number of hash tables.
    *                rows: number of rows per band. Generally, more rows yield higher precision.
    *                      Note that this is often called `k`, or the number of functions per hash table.
    *                width: width of the interval that determines two floating-point hashed values are equivalent.
    *
    */
  final class L2(override val mapping: Mapping.L2Lsh) extends LshFunction[Mapping.L2Lsh, Vec.DenseFloat, StoredVec.DenseFloat] {
    override val exact: ExactSimilarityFunction[Vec.DenseFloat, StoredVec.DenseFloat] = ExactSimilarityFunction.L2

    import mapping._
    private implicit val rng: Random = new Random(0)
    private val hashVecs: Array[Vec.DenseFloat] = (0 until (bands * rows)).map(_ => Vec.DenseFloat.random(dims)).toArray
    private val biases: Array[Float] = (0 until (bands * rows)).map(_ => rng.nextFloat() * width).toArray

    override def apply(v: Vec.DenseFloat): Array[Array[Byte]] = {
      val bandHashes = collection.mutable.Set.empty[Int]
      var ixBandHashes = 0
      var ixHashVecs = 0
      while (ixBandHashes < bands) {
        var bandHash = ixBandHashes
        var ixRows = 0
        while (ixRows < rows) {
          val hash = math.floor((hashVecs(ixHashVecs).dot(v) + biases(ixHashVecs)) / width).toInt
          bandHash = (31 * bandHash + hash) % HASH_PRIME // TODO: is this a sufficient Pairing function?
          ixRows += 1
          ixHashVecs += 1
        }
        bandHashes.add(bandHash)
        ixBandHashes += 1
      }
      // TODO: figure out how to reduce number of duplicate hashes and just use an array allocated at the start of the function.
      bandHashes.toArray.map(writeInt)
    }
  }

}
