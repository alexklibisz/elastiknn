package com.klibisz.elastiknn.reference

import com.klibisz.elastiknn.utils.Utils._
import com.carrotsearch.hppc.IntArrayList
import com.klibisz.elastiknn.{ElastiKnnVector, ElastiKnnVectorLike, SparseBoolVector}

import scala.util.{Random, Try}

object Optimizations {

  def unsortedException(little: Int, big: Int): Unit =
    throw new IllegalArgumentException(s"Called on unsorted array: $little came after $big")

  def sortedIntersectionCount(xs: IndexedSeq[Int], ys: IndexedSeq[Int]): Try[Int] = Try {
    var (n, xi, yi, xmax, ymax) = (0, 0, 0, Int.MinValue, Int.MinValue)
    while (xi < xs.length && yi < ys.length) {
      val (x, y) = (xs(xi), ys(yi))
      if (x < xmax) unsortedException(x, xmax) else xmax = x
      if (y < ymax) unsortedException(y, ymax) else ymax = y
      if (x < y) xi += 1
      else if (x > y) yi += 1
      else {
        n += 1
        xi += 1
        yi += 1
      }
    }
    while (xi < xs.length) {
      if (xs(xi) < xmax) unsortedException(xs(xi), xmax)
      xi += 1
    }
    while (yi < ys.length) {
      if (ys(yi) < xmax) unsortedException(ys(yi), xmax)
      yi += 1
    }
    n
  }

  def main(args: Array[String]): Unit = {}

}
