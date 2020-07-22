package com.klibisz.elastiknn.utils

import org.scalatest._

import scala.util.Random

class ArrayUtilsSuite extends FunSuite with Matchers {

  test("randomized quickSelect") {
    val seed = System.currentTimeMillis()
    val rng = new Random(seed)
    info(s"Using seed $seed")
    for (_ <- 0 until 99999) {
      val arr = (0 until (rng.nextInt(10000) + 1)).map(_ => rng.nextInt(Int.MaxValue)).toArray
      val k = rng.nextInt(arr.length)
      val res = ArrayUtils.quickSelect(arr, k)
      res shouldBe arr.sorted.reverse(k)
    }
  }

}
