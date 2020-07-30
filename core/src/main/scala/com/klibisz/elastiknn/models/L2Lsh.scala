package com.klibisz.elastiknn.models

import java.util.Comparator

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.storage.UnsafeSerialization.writeInt

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

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
final class L2Lsh(override val mapping: Mapping.L2Lsh) extends HashingFunction[Mapping.L2Lsh, Vec.DenseFloat, StoredVec.DenseFloat] {

  import mapping._
  private implicit val rng: Random = new Random(0)
  private val hashVecs: Array[Vec.DenseFloat] = (0 until (L * k)).map(_ => Vec.DenseFloat.random(dims)).toArray
  private val biases: Array[Float] = (0 until (L * k)).map(_ => rng.nextFloat() * r).toArray

//  private[models] val pertSets: Array[Array[Int]] = {
//
//    def isValidPertSet(ps: ArrayBuffer[Int]): Boolean =
//      ps.forall(j => !ps.contains(2 * k - 1 - j))
//
//    def shift(ps: ArrayBuffer[Int]): Option[ArrayBuffer[Int]] = {
//      val last = ps.last + 1
//      if (last > 2 * k - 1) None
//      else {
//        val shifted = new ArrayBuffer[Int](ps.size)
//      }
//      ???
//    }
//
//    def expand(ps: ArrayBuffer[Int]): Option[ArrayBuffer[Int]] = ???
//
//    // Generate expected permutation scores.
//    val c1: Float = 1f * r * r / (4 * (k + 1) * (k + 2))
//    val c2: Float = c1 * 2 * (1 - k)
//    val expectedPertScores = new Array[Float](2 * k)
//    for (j <- 1 to k)
//      expectedPertScores.update(j - 1, j * (j + 1) * c1)
//    for (j <- k + 1 to 2 * k)
//      expectedPertScores.update(j - 1, c2 * c1 * j * (j + 5))
//
//    // Generate perturbation set sorted by scores.
//    val maxPerSetLength = 20
//    val pertSets = new Array[Array[Int]](maxPerSetLength)
//    val heap = new java.util.PriorityQueue[(Float, ArrayBuffer[Int])]((o1: (Float, ArrayBuffer[Int]), o2: (Float, ArrayBuffer[Int])) =>
//      Ordering.Float.compare(o1._1, o2._1))
//    heap.add(expectedPertScores(0) -> ArrayBuffer.empty[Int])
//
//    for (i <- 0 until maxPerSetLength) {
//      var foundValid = false
//      do {
//        val (score, pset) = heap.poll()
//        foundValid = isValidPertSet(pset)
//
//      } while (!foundValid && heap.peek() != null)
//    }
//
//    ???
//  }

  override def apply(v: Vec.DenseFloat): Array[HashAndFreq] = {
    val hashes = new Array[HashAndFreq](L)
    var ixHashes = 0
    var ixHashVecs = 0
    while (ixHashes < L) {
      val lBarr = writeInt(ixHashes)
      val hashBuf = new ArrayBuffer[Byte](lBarr.length + k * 4)
      hashBuf.appendAll(lBarr)
      var ixRows = 0
      while (ixRows < k) {
        val hash = math.floor((hashVecs(ixHashVecs).dot(v) + biases(ixHashVecs)) / r).toInt
        hashBuf.appendAll(writeInt(hash))
        ixRows += 1
        ixHashVecs += 1
      }
      hashes.update(ixHashes, HashAndFreq.once(hashBuf.toArray))
      ixHashes += 1
    }
    hashes
  }
}
