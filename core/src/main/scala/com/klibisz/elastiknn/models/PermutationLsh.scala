package com.klibisz.elastiknn.models

import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.storage.UnsafeSerialization.writeInt

/**
  * Hash by repeating the indices of the highest absolute value positions proportional to their rank in the vector.
  * Based on paper: Large Scale Image Retrieval with Elasticsearch, https://dl.acm.org/doi/pdf/10.1145/3209978.3210089
  */
final class PermutationLsh(override val mapping: Mapping.PermutationLsh)
    extends HashingFunction[Mapping.PermutationLsh, Vec.DenseFloat, StoredVec.DenseFloat] {

  override def apply(vec: Vec.DenseFloat): Array[HashAndFreq] = {

    // Build a heap of the k highest-absolute-value indices.
    val ixHeap = MinMaxPriorityQueue
      .orderedBy(
        (o1: Int, o2: Int) =>
          scala.Ordering.Float
            .compare(
              math.abs(vec.values(o2)),
              math.abs(vec.values(o1))
          ))
      .maximumSize(mapping.k)
      .create[Int]()

    vec.values.indices.foreach(ixHeap.add)

    // Build the array of hashes. The number of repetitions of each hash is represented by the HashAndCount class.
    // Indexes of negative values are negated. Positive indexes are incremented by 1 and negative indexes decremented by 1
    // do avoid ambiguity of zero and negative zero. Ties are handled by repeating the tied indexes the same number of times,
    // and reducing subsequent repetition for each tie. Meaning if there's a two-way tie for 2nd place, there's no 3rd.
    val hashes = new Array[HashAndFreq](mapping.k)
    var hashesIx = 0
    var rankComplement = -1
    var currTies = 0
    var prevAbs = Float.PositiveInfinity
    while (!ixHeap.isEmpty && hashesIx < hashes.length) {
      val ix = ixHeap.removeFirst()
      val currAbs = math.abs(vec.values(ix))
      if (currAbs < prevAbs) {
        rankComplement += 1 + currTies
        prevAbs = currAbs
        currTies = 0
      } else currTies += 1
      val hash = if (vec.values(ix) >= 0) writeInt(ix + 1) else writeInt(-1 - ix)
      hashes.update(hashesIx, new HashAndFreq(hash, if (mapping.repeating) mapping.k - rankComplement else 1))
      hashesIx += 1
    }
    hashes
  }

}
