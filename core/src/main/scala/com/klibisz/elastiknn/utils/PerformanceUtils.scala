package com.klibisz.elastiknn.utils

import scala.annotation.tailrec
import scala.util.Try

trait PerformanceUtils {

  @tailrec
  final def fastfor(i: Int, pred: Int => Boolean, inc: Int => Int)(f: Int => Unit): Unit =
    if (pred(i)) {
      f(i)
      fastfor(inc(i), pred, inc)(f)
    } else ()

  final def fastfor(i: Int, pred: Int => Boolean)(f: Int => Unit): Unit = fastfor(i, pred, _ + 1)(f)

  private[elastiknn] def sortedIntersectionCount(xs: Array[Int], ys: Array[Int]): Try[Int] = Try(ArrayUtils.sortedIntersectionCount(xs, ys))

}

object PerformanceUtils extends PerformanceUtils
