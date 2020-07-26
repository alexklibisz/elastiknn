package com.klibisz.elastiknn.models

import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.storage.UnsafeSerialization.writeInt

/**
  * Hash by repeating the indices of the highest absolute value positions proportional to their rank in the vector.
  * Based on paper: Large Scale Image Retrieval with Elasticsearch, https://dl.acm.org/doi/pdf/10.1145/3209978.3210089
  */
final class MagnitudesLsh(override val mapping: Mapping.MagnitudesLsh)
    extends HashingFunction[Mapping.MagnitudesLsh, Vec.DenseFloat, StoredVec.DenseFloat] {

  override def apply(vec: Vec.DenseFloat): Array[Array[Byte]] = {

    // Build a heap of the k highest-absolute-value indices.
    val ixHeap = MinMaxPriorityQueue
      .orderedBy(
        (o1: Int, o2: Int) =>
          scala.Ordering
            .Tuple2[Float, Int]
            .compare(
              (math.abs(vec.values(o2)), o2),
              (math.abs(vec.values(o1)), o1)
          ))
      .maximumSize(mapping.k)
      .create[Int]()

    vec.values.indices.foreach(ixHeap.add)

    // Build the array of hashes. Repeat each index k - rank(index) times for a total of k + (k + 1) / 2 hashes.
    // Indexes of negative values are negated. Positive indexes are incremented by 1 and negative indexes decremented by 1
    // do avoid ambiguity of zero and negative zero. Ties are handled by repeating the index the same number of times,
    // at the expensive of dropping lower magnitude indices.
    val hashes = new Array[Array[Byte]](mapping.k * (mapping.k + 1) / 2)
    var hashesIx = 0
    var rankComplement = -1
    var prevAbs = Float.PositiveInfinity
    while (!ixHeap.isEmpty && hashesIx < hashes.length) {
      val ix = ixHeap.removeFirst()
      val currAbs = math.abs(vec.values(ix))
      if (currAbs < prevAbs) {
        rankComplement += 1
        prevAbs = currAbs
      }
      val hash = if (vec.values(ix) >= 0) {
        // System.out.println(s"Hash ${ix + 1}, ${vec.values(ix)}")
        writeInt(ix + 1)
      } else {
        // System.out.println(s"Hash ${-1 - ix}, ${vec.values(ix)}")
        writeInt(-1 - ix)
      }
      var repIx = 0
      while (repIx < mapping.k - rankComplement && hashesIx < hashes.length) {
        hashes.update(hashesIx, hash)
        hashesIx += 1
        repIx += 1
      }
    }
    hashes
  }

}
