package com.klibisz.elastiknn.models

import java.util

import com.google.common.collect.{MinMaxPriorityQueue, Sets}
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.storage.UnsafeSerialization.writeInt

import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._
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

  private def cfor(i: Int)(pred: Int => Boolean, inc: Int => Int)(f: Int => Unit): Unit = {
    var i_ = i
    while (pred(i_)) {
      f(i_)
      i_ = inc(i_)
    }
  }

  import mapping._
  private implicit val rng: Random = new Random(0)
  private val hashVecs: Array[Vec.DenseFloat] = (0 until (L * k)).map(_ => Vec.DenseFloat.random(dims)).toArray
  private val biases: Array[Float] = (0 until (L * k)).map(_ => rng.nextFloat() * r).toArray

  // Pre-compute the 3^k perturbations. ({-1, 0, 1}, {-1, 0, 1}, ... {-1, 0, 1}) k times.
  private val perturbations: Array[Array[Int]] = {
    val deltas: util.Set[Int] = Set(-1, 0, 1).asJava
    Sets
      .cartesianProduct((0 until k).map(_ => deltas): _*)
      .asScala
      .toArray
      .map(_.asScala.toArray)
      .sortBy(_.mkString(","))
  }
  private val zeroPerturbation: Array[Int] = Array.fill[Int](k)(0)

  override def apply(v: Vec.DenseFloat): Array[HashAndFreq] = hashWithProbes(v, 0)

  private def scorePerturbation(projections: Array[Float], hashes: Array[Int], perturbation: Array[Int]): Float = {
    var score = 0f
    cfor(0)(_ < k, _ + 1) { ixK =>
      val xi =
        if (perturbation(ixK) == 0) 0
        else {
          val neg = projections(ixK) - hashes(ixK) * r
          if (perturbation(ixK) == -1) neg
          else r - neg
        }
      score += xi * xi
    }
    score
  }

  def hashWithProbes(v: Vec.DenseFloat, probes: Int): Array[HashAndFreq] = {
    val probesAdjusted = perturbations.length.min(probes + 1)
    val allHashes = new Array[HashAndFreq](L * probesAdjusted)

    cfor(0)(_ < L, _ + 1) { ixL =>
      // Each hash generated for this table is prefixed with these bytes.
      val lBarr = writeInt(ixL)

      // Project and hash the vector onto the next k random vectors.
      // Need both projections and hashes to compute perturbation scores.
      val (projections, hashes): (Array[Float], Array[Int]) = {
        val parr = new Array[Float](k)
        val harr = new Array[Int](k)
        cfor(0)(_ < k, _ + 1) { ixK =>
          val p = hashVecs(ixL * k + ixK).dot(v) + biases(ixL * k + ixK)
          parr.update(ixK, p)
          harr.update(ixK, math.floor(p / r).toInt)
        }
        (parr, harr)
      }

      // Sort the perturbations by their score.
      val perturbationHeap = MinMaxPriorityQueue
        .orderedBy((p1: Array[Int], p2: Array[Int]) => {
          Ordering.Float.compare(
            scorePerturbation(projections, hashes, p1),
            scorePerturbation(projections, hashes, p2)
          )
        })
        .maximumSize(probesAdjusted)
        .create[Array[Int]]()

      // Special case for zero probes.
      if (probes == 0) perturbationHeap.add(zeroPerturbation)
      else perturbations.foreach(perturbationHeap.add)

      cfor(0)(_ < probesAdjusted && !perturbationHeap.isEmpty, _ + 1) { ixP =>
        val hashBuf = new ArrayBuffer[Byte](lBarr.length + k * 4)
        hashBuf.appendAll(lBarr)
        val perturbation = perturbationHeap.removeFirst()
        cfor(0)(_ < k, _ + 1) { ixK =>
          hashBuf.appendAll(writeInt(hashes(ixK) + perturbation(ixK)))
        }
        allHashes.update(ixL * probesAdjusted + ixP, HashAndFreq.once(hashBuf.toArray))
      }
    }

    allHashes
  }

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

}
