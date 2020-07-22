package com.klibisz.elastiknn.utils

import org.scalatest._

import scala.util.Random

class ArrayUtilsSuite extends FunSuite with Matchers with Inspectors {

  test("kLargest example") {
    val counts: Array[Short] = Array(2, 2, 8, 7, 4, 4)
    val k = 3
    val res = ArrayUtils.kLargestIndices(counts, k)
    res shouldBe Array(2, 3, 4, 5)
  }

  test("kLargest randomized") {
    val seed = System.currentTimeMillis()
    val rng = new Random(seed)
    info(s"Using seed $seed")
    for (_ <- 0 until 999) {
      val counts = (0 until (rng.nextInt(10000) + 1)).map(_ => rng.nextInt(Short.MaxValue).toShort).toArray
      val k = rng.nextInt(counts.length)
      val res = ArrayUtils.kLargestIndices(counts, k)
      val kthLargest = counts.sorted.reverse(k)
      forAll(res)(counts(_) shouldBe >=(kthLargest))
      forAll(counts.indices.diff(res))(counts(_) shouldBe <(kthLargest))
    }
  }

}
