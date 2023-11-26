package com.klibisz.elastiknn.search

import org.apache.lucene.search.KthGreatest
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class KthGreatestSuite extends AnyFunSuite with Matchers {

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

  test("example") {
    val counts: Array[Int] = Array(2, 2, 8, 7, 4, 4)
    val res = KthGreatest.kthGreatest(counts, 3)
    res.kthGreatest shouldBe 4
    res.numGreaterThan shouldBe 2
    res.numNonZero shouldBe 6
  }

  test("randomized") {
    val seed = System.currentTimeMillis()
    val rng = new Random(seed)
    info(s"Using seed $seed")
    for (_ <- 0 until 999) {
      val counts = (0 until (rng.nextInt(10000) + 1)).map(_ => rng.nextInt(Short.MaxValue)).toArray
      val k = rng.nextInt(counts.length)
      val res = KthGreatest.kthGreatest(counts, k)
      res.kthGreatest shouldBe counts.sorted.reverse(k)
      res.numGreaterThan shouldBe counts.count(_ > res.kthGreatest)
      res.numNonZero shouldBe counts.count(_ != 0)
    }
  }

  test("all zero except one") {
    val counts = Array[Int](50, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    val res = KthGreatest.kthGreatest(counts, 3)
    res.kthGreatest shouldBe 0
    res.numGreaterThan shouldBe 1
    res.numNonZero shouldBe 1
  }

  test("all zero") {
    val counts = Array[Int](0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    val res = KthGreatest.kthGreatest(counts, 3)
    res.kthGreatest shouldBe 0
    res.numGreaterThan shouldBe 0
    res.numNonZero shouldBe 0
  }

}
