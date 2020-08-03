package com.klibisz.elastiknn.models

import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.storage.UnsafeSerialization.{numBytesInInt, writeInt}

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
  * Locality sensitive hashing for L2 similarity based on MMDS Chapter 3.
  * Also drew some inspiration from this closed pull request: https://github.com/elastic/elasticsearch/pull/44374
  * Multi-probe is based on 2007 paper by Qin, et. al. and uses the naive method for choosing perturbation vectors.
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
  import L2Lsh.Multiprobe._

  // Instantiate a and b parameters for L * k hash functions.
  private implicit val rng: Random = new Random(0)
  private val A: Array[Vec.DenseFloat] = (0 until (L * k)).map(_ => Vec.DenseFloat.random(dims)).toArray
  private val B: Array[Float] = (0 until (L * k)).map(_ => rng.nextFloat() * r).toArray

  private val numPerturbations: Int = math.pow(2, k).toInt

  override def apply(v: Vec.DenseFloat): Array[HashAndFreq] = hashWithProbes(v, 0)

  def hashWithProbes(v: Vec.DenseFloat, probes: Int): Array[HashAndFreq] = {
    val probesAdjusted = numPerturbations.min(probes)
    val allHashes = new Array[HashAndFreq](L * (probesAdjusted + 1))

    cfor(0)(_ < L, _ + 1) { ixL =>
      // Each hash generated for this table is prefixed with these bytes.
      val lBarr = writeInt(ixL)

      // If you don't need probing, e.g. when indexing, just compute the hashes.
      if (probesAdjusted == 0) {
        val hashBuf = new ArrayBuffer[Byte](lBarr.length + k * numBytesInInt)
        hashBuf.appendAll(lBarr)
        cfor(0)(_ < k, _ + 1) { ixK =>
          val a = A(ixL * k + ixK)
          val b = B(ixL * k + ixK)
          val h = math.floor(math.floor((a.dot(v) + b) / r)).toInt
          hashBuf.appendAll(writeInt(h))
        }
        allHashes.update(ixL, HashAndFreq.once(hashBuf.toArray))
      }
      // Otherwise there is some more work to generate probed hashes.
      else {
        // Project and hash the vector onto the next k random vectors.
        // Populate and sort 2 * k non-zero perturbations.
        val (hashes, perturbations): (Array[Int], Array[Perturbation]) = {
          val hashes = new Array[Int](k)
          val perturbations = new Array[Perturbation](2 * k)
          cfor(0)(_ < k, _ + 1) { ixK =>
            val p = A(ixL * k + ixK).dot(v) + B(ixL * k + ixK)
            val h = math.floor(p / r).toInt
            hashes.update(ixK, h)
            perturbations.update(ixK * 2, Perturbation(ixK, pos=false, p, h, r))
            perturbations.update(ixK * 2 + 1, Perturbation(ixK, pos=true, p, h, r))
          }
          perturbations.sortBy(_.x)
          (hashes, perturbations)
        }

        println(perturbations.length)

        // Create and select the best perturbation sets using Algorithm 1 from Qin et. al.
        val bestPsets: Array[PerturbationSet] = {
          val heap: MinMaxPriorityQueue[PerturbationSet] = MinMaxPriorityQueue
            .orderedBy((o1: PerturbationSet, o2: PerturbationSet) => if (o1.score() < o2.score()) -1 else 1)
            .maximumSize(probesAdjusted)
            .create[PerturbationSet]()
          val best = new Array[PerturbationSet](probesAdjusted)
          heap.add(PerturbationSet(perturbations.head))
          cfor(0)(_ < probesAdjusted, _ + 1) { ixBestPsets =>
            do {
              val Ai = heap.removeFirst()
              heap.add(Ai.shift(perturbations))
              heap.add(Ai.expand(perturbations))
            } while (!heap.peekFirst().valid())
            best.update(ixBestPsets, heap.removeFirst())
          }
          best
        }

        // Generate, append hashes for the empty pset (i.e. no probing) and each of the best psets.
        ???
      }
    }

    allHashes
  }

}

object L2Lsh {

  private object Multiprobe {

    case class Perturbation(i: Int, pos: Boolean, projection: Float, hash: Int, r: Int) {
      // $x_i(\delta = -1) = f_i(q) - h_i(q) * r$, $x_i(\delta = 1) = r - x_i(-1)$.
      val x: Float = if (pos) r - (projection - hash * r) else projection - hash * r
    }

    class PerturbationSet private (state: Map[Int, Perturbation], maxIndex: Int) {
      def add(p: Perturbation): PerturbationSet = ???
      def score(): Float = {
        ???
      }
      def valid(): Boolean = {
        ???
      }
      def shift(all: Array[Perturbation]): PerturbationSet = {
        ???
      }
      def expand(all: Array[Perturbation]): PerturbationSet = {
        ???
      }
    }

    object PerturbationSet {
      def apply(k: Int): PerturbationSet =
        new PerturbationSet(new Array[Perturbation](k), new Array[Perturbation](k))
      def apply(p: Perturbation): PerturbationSet = if ()

    }

  }

}
