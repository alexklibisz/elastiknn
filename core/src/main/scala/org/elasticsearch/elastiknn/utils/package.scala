package org.elasticsearch.elastiknn

import scala.annotation.tailrec
import scala.util.Try

package object utils {

  @tailrec
  private[elastiknn] def fastfor(i: Int, pred: Int => Boolean, inc: Int => Int)(f: Int => Unit): Unit =
    if (pred(i)) {
      f(i)
      fastfor(inc(i), pred, inc)(f)
    } else ()

  private[elastiknn] def fastfor(i: Int, pred: Int => Boolean)(f: Int => Unit): Unit = fastfor(i, pred, _ + 1)(f)

  private def unsortedException(little: Int, big: Int): Unit =
    throw new IllegalArgumentException(s"Called on unsorted array: $little came after $big")

  private[elastiknn] def sortedIntersectionCount(xs: IndexedSeq[Int], ys: IndexedSeq[Int]): Try[Int] = Try {
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

}
