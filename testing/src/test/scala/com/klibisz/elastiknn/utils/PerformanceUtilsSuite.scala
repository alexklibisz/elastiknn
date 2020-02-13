package com.klibisz.elastiknn.utils

import org.scalatest.{FunSpec, Matchers}

import scala.util.{Failure, Random, Success}

class PerformanceUtilsSuite extends FunSpec with Matchers with PerformanceUtils {

  describe("sorted intersection count") {
    val rng = new Random(0)
    val hundred = (0 to 100).toArray
    val empty = Array.empty[Int]
    def randRange: Array[Int] = (0 to rng.nextInt(50)).toArray

    it("computes the intersection given an empty traversable") {
      sortedIntersectionCount(hundred, empty) shouldBe Success(0)
      sortedIntersectionCount(empty, hundred) shouldBe Success(0)
      sortedIntersectionCount(empty, empty) shouldBe Success(0)
    }

    it("computes the intersection of random sorted traversables") {
      for (_ <- 0 until 100) {
        val xs = randRange.map(_ => rng.nextInt(1000)).sorted
        val ys = randRange.map(_ => rng.nextInt(1000)).sorted
        sortedIntersectionCount(xs, ys) shouldBe Success(xs.intersect(ys).length)
      }
    }

    it("fails to compute intersection of two unsorted traversables") {
      for (_ <- 0 until 100) {
        val xs = randRange.map(_ => rng.nextInt(1000)) ++ Seq(Int.MinValue)
        val ys = randRange.map(_ => rng.nextInt(1000)) ++ Seq(Int.MaxValue)
        sortedIntersectionCount(xs, ys) should matchPattern {
          case Failure(_) =>
        }
      }
    }

  }

}
