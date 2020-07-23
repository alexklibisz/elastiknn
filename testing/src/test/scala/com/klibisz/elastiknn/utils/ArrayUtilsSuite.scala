package com.klibisz.elastiknn.utils

import org.scalatest._

import scala.util.Random

class ArrayUtilsSuite extends FunSuite with Matchers {

  test("kLargest example") {
    val counts: Array[Short] = Array(2, 2, 8, 7, 4, 4)
    val res = ArrayUtils.kthGreatest(counts, 3)
    res shouldBe 4
  }

  test("kLargest randomized") {
    val seed = System.currentTimeMillis()
    val rng = new Random(seed)
    info(s"Using seed $seed")
    for (_ <- 0 until 999) {
      val counts = (0 until (rng.nextInt(10000) + 1)).map(_ => rng.nextInt(Short.MaxValue).toShort).toArray
      val k = rng.nextInt(counts.length)
      val res = ArrayUtils.kthGreatest(counts, k)
      res shouldBe counts.sorted.reverse(k)
    }
  }

}
