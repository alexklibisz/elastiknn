package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.{BitBuffer, StoredVec}
import com.klibisz.elastiknn.storage.UnsafeSerialization.writeInt

import scala.annotation.tailrec
import scala.util.Random

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
final class HammingLsh(override val mapping: Mapping.HammingLsh)
    extends HashingFunction[Mapping.HammingLsh, Vec.SparseBool, StoredVec.SparseBool] {
  override val exact: ExactSimilarityFunction[Vec.SparseBool, StoredVec.SparseBool] = ExactSimilarityFunction.Hamming

  import mapping._
  private val rng: Random = new Random(0)
  private val zeroUntilL: Array[Int] = (0 until L).toArray

  private case class Position(vecIndex: Int, hashIndexes: Array[Int])

  // Sample L * k Positions, sorted by vecIndex for more efficient intersection in apply method.
  private val sampledPositions: Array[Position] = {
    case class Pair(vecIndex: Int, hashIndex: Int)
    @tailrec
    def sampleIndicesWithoutReplacement(n: Int, acc: Set[Int] = Set.empty, i: Int = rng.nextInt(dims)): Array[Int] =
      if (acc.size == n.min(dims)) acc.toArray
      else if (acc(i)) sampleIndicesWithoutReplacement(n, acc, rng.nextInt(dims))
      else sampleIndicesWithoutReplacement(n, acc + i, rng.nextInt(dims))

    // If L * k <= dims, just sample vec indices once, guaranteeing no repetition.
    val pairs: Array[Pair] = if ((L * k) <= dims) {
      sampleIndicesWithoutReplacement(L * k).zipWithIndex.map {
        case (vi, hi) => Pair(vi, hi % L) // Careful setting hashIndex, so it's < L.
      }
    } else {
      zeroUntilL.flatMap(hi => sampleIndicesWithoutReplacement(k).map(vi => Pair(vi, hi)))
    }
    pairs
      .groupBy(_.vecIndex)
      .mapValues(_.map(_.hashIndex))
      .map(Position.tupled)
      .toArray
      .sortBy(_.vecIndex)
  }

  override def apply(vec: Vec.SparseBool): Array[Array[Byte]] = {
    val hashBuffers = zeroUntilL.map(l => new BitBuffer.IntBuffer(writeInt(l)))
    var (ixTrueIndices, ixSampledPositions) = (0, 0)
    while (ixTrueIndices < vec.trueIndices.length && ixSampledPositions < sampledPositions.length) {
      val pos = sampledPositions(ixSampledPositions)
      val trueIndex = vec.trueIndices(ixTrueIndices)
      // The true index wasn't sampled, move along.
      if (pos.vecIndex > trueIndex) ixTrueIndices += 1
      // The sampled index is negative, append a zero.
      else if (pos.vecIndex < trueIndex) {
        pos.hashIndexes.foreach(hi => hashBuffers(hi).putZero())
        ixSampledPositions += 1
      }
      // The sampled index is positive, append a one.
      else {
        pos.hashIndexes.foreach(hi => hashBuffers(hi).putOne())
        ixTrueIndices += 1
      }
    }
    // Traverse the remaining sampled positions, if any, appending zeros.
    while (ixSampledPositions < sampledPositions.length) {
      val pos = sampledPositions(ixSampledPositions)
      pos.hashIndexes.foreach(hi => hashBuffers(hi).putZero())
      ixSampledPositions += 1
    }
    hashBuffers.map(_.toByteArray)
  }
}
