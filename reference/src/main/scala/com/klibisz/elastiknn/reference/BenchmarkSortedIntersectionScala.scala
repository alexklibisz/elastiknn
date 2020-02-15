package com.klibisz.elastiknn.reference

import com.klibisz.elastiknn.utils.PerformanceUtils

import scala.util.Random

object BenchmarkSortedIntersectionScala {

  def main(args: Array[String]): Unit = {

    val rng = new Random(0)
    val n = 5000000
    val xs = (1 to 1000).map(_ => rng.nextInt(10000)).toArray.sorted
    val ys = (1 to 1000).map(_ => rng.nextInt(10000)).toArray.sorted

    Thread.sleep(5000)

    val t0 = System.currentTimeMillis()
    PerformanceUtils.fastfor(0, _ < n, _ + 1) { i =>
      if (i % 2 == 0) PerformanceUtils.sortedIntersectionCount(xs, ys)
      else PerformanceUtils.sortedIntersectionCount(ys, xs)
    }
    println((System.currentTimeMillis() - t0) / 1000.0)

  }

}
