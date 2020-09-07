package com.klibisz.elastiknn.search

import org.apache.lucene.search.KthGreatest
import org.scalatest._

import scala.util.Random

class KthGreatestSuite extends FunSuite with Matchers {

  test("example") {
    val counts: Array[Short] = Array(2, 2, 8, 7, 4, 4)
    val res = KthGreatest.kthGreatest(counts, 3)
    res.kthGreatest shouldBe 4
  }

  test("bad args") {
    an[IllegalArgumentException] shouldBe thrownBy {
      KthGreatest.kthGreatest(Array.empty, 3)
    }
    an[IllegalArgumentException] shouldBe thrownBy {
      KthGreatest.kthGreatest(Array(1, 2, 3), -1)
    }
    an[IllegalArgumentException] shouldBe thrownBy {
      KthGreatest.kthGreatest(Array(1, 2, 3), 4)
    }
  }

  test("randomized") {
    val seed = System.currentTimeMillis()
    val rng = new Random(seed)
    info(s"Using seed $seed")
    for (_ <- 0 until 999) {
      val counts = (0 until (rng.nextInt(10000) + 1)).map(_ => rng.nextInt(Short.MaxValue).toShort).toArray
      val k = rng.nextInt(counts.length)
      val res = KthGreatest.kthGreatest(counts, k)
      res.kthGreatest shouldBe counts.sorted.reverse(k)
    }
  }

  test("all zero except one") {
    val counts = Array[Short](50, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    KthGreatest.kthGreatest(counts, 3)
  }

}
