package com.klibisz.elastiknn.models

import com.google.common.collect.MinMaxPriorityQueue
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.storage.{StoredVec, UnsafeSerialization}

/**
  *
  * @param mapping
  */
final class MagnitudesLsh(override val mapping: Mapping.MagnitudesLsh)
    extends HashingFunction[Mapping.MagnitudesLsh, Vec.DenseFloat, StoredVec.DenseFloat] {

  override def apply(v1: Vec.DenseFloat): Array[Array[Byte]] = {
    // Find the k highest-magnitude values.
    val heap = MinMaxPriorityQueue
      .orderedBy(
        (o1: Int, o2: Int) =>
          scala.Ordering.Float.compare(
            math.abs(v1.values(o2)),
            math.abs(v1.values(o1))
        ))
      .maximumSize(mapping.k)
      .create[Int]()
    v1.values.indices.foreach(heap.add)

    // Build the hash array by repeating each index k - rank(index) times.
    val hashes = new Array[Array[Byte]](mapping.k * (mapping.k + 1) / 2)
    var (hashexIx, rank) = (0, 0)
    while (!heap.isEmpty) {
      val hash = UnsafeSerialization.writeInt(heap.removeFirst())
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

object Foo extends App {
  val mlsh = new MagnitudesLsh(Mapping.MagnitudesLsh(5, 3))
  mlsh(Vec.DenseFloat(Array(5f, -4f, -1f, 0f, 4f)))
}
