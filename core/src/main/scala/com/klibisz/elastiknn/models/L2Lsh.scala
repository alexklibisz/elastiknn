package com.klibisz.elastiknn.models

import java.util

import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.storage.UnsafeSerialization._

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
  * Locality sensitive hashing for L2 similarity based on MMDS Chapter 3.
  * Also drew some inspiration from this closed pull request: https://github.com/elastic/elasticsearch/pull/44374
  * Multi-probe is based on 2007 paper by Qin, et. al. and uses the naive method for choosing perturbation vectors.
  */
final class L2Lsh(override val mapping: Mapping.L2Lsh, A: Array[Vec.DenseFloat], B: Array[Float])
    extends HashingFunction[Mapping.L2Lsh, Vec.DenseFloat, StoredVec.DenseFloat] {

  import L2Lsh._
  import mapping._

  // Each hash value is prefixed by the index of its table to virtually eliminate false positive collisions.
  private val byteArrayPrefixes: Array[Array[Byte]] = (0 until L).map(writeInt).toArray

  // 3 possible perturbations for each of k hashes. Subtract one for the all-zeros case.
  private val maxProbesPerTable: Int = math.pow(3, k).toInt - 1

  override def apply(v: Vec.DenseFloat): Array[HashAndFreq] = hashWithProbes(v, 0)

  def hashWithProbes(v: Vec.DenseFloat, probesPerTable: Int): Array[HashAndFreq] = {
    val allHashes = new Array[HashAndFreq](L * (1 + probesPerTable.min(maxProbesPerTable).max(0)))

    // If you don't need probing, (when indexing or probes = 0), just compute the hashes.
    if (allHashes.length == L) {
      cfor(0)(_ < L, _ + 1) { ixL =>
        val p = byteArrayPrefixes(ixL)
        val buf = new ArrayBuffer[Byte](p.length + k * numBytesInInt)
        buf.appendAll(p)
        cfor(0)(_ < k, _ + 1) { ixk =>
          val a = A(ixL * k + ixk)
          val b = B(ixL * k + ixk)
          val h = math.floor((a.dot(v) + b) / r).toInt
          buf.appendAll(writeInt(h))
        }
        allHashes.update(ixL, HashAndFreq.once(buf.toArray))
      }
      allHashes
    }

    // Otherwise, pick the perturbation sets most likely to find near misses.
    else {

      // Collect the non-perturbed hashes and all possible single-hash perturbations.
      val sortedPerturbations = Array.fill(L)(new Array[Perturbation](k * 2))
      val zeroPerturbations = new Array[Perturbation](L * k)
      cfor(0)(_ < L, _ + 1) { ixL =>
        val p = byteArrayPrefixes(ixL)
        val buf = new ArrayBuffer[Byte](p.length + k * numBytesInInt)
        buf.appendAll(p)
        cfor(0)(_ < k, _ + 1) { ixk =>
          val a = A(ixL * k + ixk)
          val b = B(ixL * k + ixk)
          val f = a.dot(v) + b
          val h = math.floor(f / r).toInt
          val dneg = f - h * r
          sortedPerturbations(ixL).update(ixk * 2 + 0, Perturbation(ixL, ixk, -1, f, h, math.abs(dneg)))
          sortedPerturbations(ixL).update(ixk * 2 + 1, Perturbation(ixL, ixk, 1, f, h, math.abs(r - dneg)))
          zeroPerturbations.update(ixL * k + ixk, Perturbation(ixL, ixk, 0, f, h, 0))
          buf.appendAll(writeInt(h))
        }
        allHashes.update(ixL, HashAndFreq.once(buf.toArray))
      }

      // Use algorithm 1 from Qin et. al. to pick the top perturbation sets.
      val heap = MinMaxPriorityQueue
        .orderedBy((o1: PerturbationSet, o2: PerturbationSet) => Ordering.Float.compare(o1.absDistsSum, o2.absDistsSum))
        .create[PerturbationSet]()

      // Sort the perturbations in ascending order by their distance value.
      // Add the head of each sorted array to the heap.
      cfor(0)(_ < L, _ + 1) { ixL =>
        util.Arrays.sort(sortedPerturbations(ixL),
                         (o1: Perturbation, o2: Perturbation) => Ordering.Float.compare(o1.absDistance, o2.absDistance))
        heap.add(PerturbationSet(sortedPerturbations(ixL).head))
      }

      // Start at L because the first L non-perturbed hashes were added above.
      cfor(L)(_ < allHashes.length, _ + 1) { ixAllHashes =>
        // Extract the top perturbation set and add the shifted/expanded versions.
        // This implementation assumes that shift/expand can only return valid perturbation sets, hence the options.
        val Ai = heap.removeFirst()
        val As = shift(sortedPerturbations(Ai.ixL), Ai)
        val Ae = expand(sortedPerturbations(Ai.ixL), Ai)
        As.foreach(heap.add)
        Ae.foreach(heap.add)

        // Generate the hash value for Ai. If ixk is unperturbed, access the zeroPerturbations from above.
        val p = byteArrayPrefixes(Ai.ixL)
        val buf = new ArrayBuffer[Byte](p.length + k * numBytesInInt)
        buf.appendAll(p)
        cfor(0)(_ < k, _ + 1) { ixk =>
          val pert = Ai.members.getOrElse(ixk, zeroPerturbations(Ai.ixL * k + ixk))
          buf.appendAll(writeInt(pert.hash + pert.delta))
        }
        allHashes.update(ixAllHashes, HashAndFreq.once(buf.toArray))
      }

      allHashes
    }
  }

}

object L2Lsh {

  def apply(mapping: Mapping.L2Lsh): L2Lsh = {
    import mapping._
    implicit val rng: Random = new Random(0)
    val A: Array[Vec.DenseFloat] = (0 until (L * k)).map(_ => Vec.DenseFloat.random(dims)).toArray
    val B: Array[Float] = (0 until (L * k)).map(_ => rng.nextFloat() * r).toArray
    new L2Lsh(mapping, A, B)
  }

  private case class Perturbation(ixL: Int, ixk: Int, delta: Int, projection: Float, hash: Int, absDistance: Float)

  private case class PerturbationSet(ixL: Int, members: Map[Int, Perturbation], ixMax: Int, absDistsSum: Float)

  private object PerturbationSet {
    def apply(perturbation: Perturbation): PerturbationSet =
      PerturbationSet(perturbation.ixL, Map(perturbation.ixk -> perturbation), 0, perturbation.absDistance)
  }

  // TODO: this should only return None if pset.ixMax + 1 == sortedPerturbations.length.
  // Otherwise it should compute the _next valid_ shifted perturbation set.
  private def shift(sortedPerturbations: Array[Perturbation], pset: PerturbationSet): Option[PerturbationSet] = {

    @tailrec
    def rec(ixMax: Int): Option =
      if (ixMax + 1 == sortedPerturbations.length) None
      else {
        val nextMax = sortedPerturbations(ixMax + 1)
        val currMax = sortedPerturbations(ixMax)
        // If the pset already contains a perturbation at this index and the current max doesn't belong to
        if (pset.members.contains(nextMax.ixk) && currMax.ixk != nextMax.ixk) rec(ixMax + 1)
        else
          Some(
            pset.copy(
              members = pset.members - currMax.ixk + (nextMax.ixk -> nextMax),
              absDistsSum = pset.absDistsSum - currMax.absDistance + nextMax.absDistance,
              ixMax = pset.ixMax + 1
            ))
      }

    if (pset.ixMax + 1 == sortedPerturbations.length) None
    else {
      val nextMax = sortedPerturbations(pset.ixMax + 1)
      val currMax = sortedPerturbations(pset.ixMax)
      if (pset.members.contains(nextMax.ixk) && currMax.ixk != nextMax.ixk) None
      else
        Some(
          pset.copy(
            members = pset.members - currMax.ixk + (nextMax.ixk -> nextMax),
            absDistsSum = pset.absDistsSum - currMax.absDistance + nextMax.absDistance,
            ixMax = pset.ixMax + 1
          ))
    }
  }

  private def expand(sortedPerturbations: Array[Perturbation], pset: PerturbationSet): Option[PerturbationSet] =
    if (pset.ixMax + 1 == sortedPerturbations.length) None
    else {
      val nextMax = sortedPerturbations(pset.ixMax + 1)
      if (pset.members.contains(nextMax.ixk)) None
      else
        Some(
          pset.copy(
            members = pset.members + (nextMax.ixk -> nextMax),
            absDistsSum = pset.absDistsSum + nextMax.absDistance,
            ixMax = pset.ixMax + 1
          )
        )
    }

}
