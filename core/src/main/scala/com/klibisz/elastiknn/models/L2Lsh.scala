package com.klibisz.elastiknn.models

import java.util

import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.storage.UnsafeSerialization._

import scala.annotation.tailrec
import scala.util.Random

/**
  * Locality sensitive hashing with multiprobe hashing for L2 similarity based on MMDS Chapter 3 and Qin, et. al. 2007.
  * Also drew some inspiration from this closed pull request: https://github.com/elastic/elasticsearch/pull/44374
  *
  * The multiprobe implementation is the same as Qin, et. al, with some subtle differences:
  * - Doesn't use the score estimation described in section 4.5. This doesn't seem necessary as generating perturbation
  *   sets is not even showing up in the profiler.
  * - Keeps a single heap of perturbation sets across all tables. They actually mention this as an option, but it's not
  *   clear if they implement it.
  * - The shift and expand methods are smart enough to always generate valid perturbation sets, so you'll never append
  *   an invalid one to the heap. This simplifies the logic for Algorithm 1.
  */
final class L2Lsh(override val mapping: Mapping.L2Lsh) extends HashingFunction[Mapping.L2Lsh, Vec.DenseFloat, StoredVec.DenseFloat] {

  import L2Lsh._
  import mapping._

  implicit val rng: Random = new Random(0)
  val A: Array[Vec.DenseFloat] = (0 until (L * k)).map(_ => Vec.DenseFloat.random(dims)).toArray
  val B: Array[Float] = (0 until (L * k)).map(_ => rng.nextFloat() * r).toArray

  // 3 possible perturbations for each of k hashes. Subtract one for the all-zeros case.
  private val maxProbesPerTable: Int = math.pow(3, k).toInt - 1

  override def apply(v: Vec.DenseFloat): Array[HashAndFreq] = hashWithProbes(v, 0)

  def hashWithProbes(v: Vec.DenseFloat, probesPerTable: Int): Array[HashAndFreq] = {
    val allHashes = new Array[HashAndFreq](L * (1 + probesPerTable.min(maxProbesPerTable).max(0)))

    // If you don't need probing, (when indexing or probes = 0), just compute the hashes.
    if (allHashes.length == L) {
      cfor(0)(_ < L, _ + 1) { ixL =>
        val hashInts = new Array[Int](1 + k)
        hashInts.update(0, ixL)
        cfor(0)(_ < k, _ + 1) { ixk =>
          val a = A(ixL * k + ixk)
          val b = B(ixL * k + ixk)
          val h = math.floor((a.dot(v) + b) / r).toInt
          hashInts.update(ixk + 1, h)
        }
        allHashes.update(ixL, HashAndFreq.once(writeInts(hashInts)))
      }
      allHashes
    }

    // Otherwise, pick the perturbation sets most likely to find near misses.
    else {

      // Collect the non-perturbed hashes and all possible single-hash perturbations.
      val sortedPerturbations = Array.fill(L)(new Array[Perturbation](k * 2))
      val zeroPerturbations = new Array[Perturbation](L * k)
      cfor(0)(_ < L, _ + 1) { ixL =>
        val hashInts = new Array[Int](1 + k)
        hashInts.update(0, ixL)
        cfor(0)(_ < k, _ + 1) { ixk =>
          val a = A(ixL * k + ixk)
          val b = B(ixL * k + ixk)
          val f = a.dot(v) + b
          val h = math.floor(f / r).toInt
          val dneg = f - h * r
          sortedPerturbations(ixL).update(ixk * 2 + 0, Perturbation(ixL, ixk, -1, f, h, math.abs(dneg)))
          sortedPerturbations(ixL).update(ixk * 2 + 1, Perturbation(ixL, ixk, 1, f, h, math.abs(r - dneg)))
          zeroPerturbations.update(ixL * k + ixk, Perturbation(ixL, ixk, 0, f, h, 0))
          hashInts.update(ixk + 1, h)
        }
        allHashes.update(ixL, HashAndFreq.once(writeInts(hashInts)))
      }

      // Use algorithm 1 from Qin et. al. to pick the top perturbation sets.
      val heap = MinMaxPriorityQueue
        .orderedBy((o1: PerturbationSet, o2: PerturbationSet) => if (o1.absDistsSum < o2.absDistsSum) -1 else 1)
        .create[PerturbationSet]()

      // Sort the perturbations in ascending order by their distance value.
      // Add the head of each sorted array to the heap.
      cfor(0)(_ < L, _ + 1) { ixL =>
        util.Arrays.sort(sortedPerturbations(ixL), (o1: Perturbation, o2: Perturbation) => if (o1.absDistance < o2.absDistance) -1 else 1)
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
        val hashInts = new Array[Int](1 + k)
        hashInts.update(0, Ai.ixL)
        cfor(0)(_ < k, _ + 1) { ixk =>
          val pert = Ai.members.getOrElse(ixk, zeroPerturbations(Ai.ixL * k + ixk))
          hashInts.update(ixk + 1, pert.hash + pert.delta)
        }
        allHashes.update(ixAllHashes, HashAndFreq.once(writeInts(hashInts)))
      }

      allHashes
    }
  }

}

object L2Lsh {

  private case class Perturbation(ixL: Int, ixk: Int, delta: Int, projection: Float, hash: Int, absDistance: Float)

  private case class PerturbationSet(ixL: Int, members: Map[Int, Perturbation], ixMax: Int, absDistsSum: Float)

  private object PerturbationSet {
    def apply(perturbation: Perturbation): PerturbationSet =
      PerturbationSet(perturbation.ixL, Map(perturbation.ixk -> perturbation), 0, perturbation.absDistance)
  }

  @tailrec
  private def shift(sortedPerturbations: Array[Perturbation], pset: PerturbationSet): Option[PerturbationSet] =
    // Hit the end of the perturbation list, can't add more.
    if (pset.ixMax + 1 == sortedPerturbations.length) None
    else {
      val currMax = sortedPerturbations(pset.ixMax)
      val nextMax = sortedPerturbations(pset.ixMax + 1)
      val nextPset = pset.copy(
        members = pset.members - currMax.ixk + (nextMax.ixk -> nextMax),
        absDistsSum = pset.absDistsSum - currMax.absDistance + nextMax.absDistance,
        ixMax = pset.ixMax + 1
      )
      // In some cases shifting can create an invalid pset, meaning there are two perturbations on the same index.
      // In that case, call shift recursively to get rid of the invalid pair of perturbations.
      if (pset.members.contains(nextMax.ixk) && currMax.ixk != nextMax.ixk) shift(sortedPerturbations, nextPset)
      else Some(nextPset)
    }

  private def expand(sortedPerturbations: Array[Perturbation], pset: PerturbationSet): Option[PerturbationSet] = {
    if (pset.ixMax + 1 == sortedPerturbations.length) None
    else {
      val nextMax = sortedPerturbations(pset.ixMax + 1)
      val nextPset = pset.copy(
        members = pset.members + (nextMax.ixk -> nextMax),
        absDistsSum = pset.absDistsSum + nextMax.absDistance,
        ixMax = pset.ixMax + 1
      )
      // Sometimes expanding creates an invalid pset. Shifting cleans it up.
      if (pset.members.contains(nextMax.ixk)) shift(sortedPerturbations, nextPset)
      else Some(nextPset)
    }
  }

}
