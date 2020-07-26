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
          scala.Ordering.Float.compare(
            math.abs(vec.values(o2)),
            math.abs(vec.values(o1))
        ))
      .maximumSize(mapping.k)
      .create[Int]()

    vec.values.indices.foreach(ixHeap.add)

    // Build the hash array by repeating each index k - rank(index) times for a total of k + (k + 1) / 2 hashes.
    // Indexes of negative values are negated.
    val hashes = new Array[Array[Byte]](mapping.k * (mapping.k + 1) / 2)
    var (hashexIx, rank) = (0, 0)
    while (!ixHeap.isEmpty) {
      val ix = ixHeap.removeFirst()
      val hash = if (vec.values(ix) >= 0) writeInt(ix) else writeInt(vec.values.length + ix)
      var repIx = 0
      while (repIx < mapping.k - rank) {
        hashes.update(hashexIx, hash)
        hashexIx += 1
        repIx += 1
      }
      rank += 1
    }

    hashes
  }
}
