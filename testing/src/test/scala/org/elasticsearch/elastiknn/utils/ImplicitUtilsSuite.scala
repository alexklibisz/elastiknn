package org.elasticsearch.elastiknn.utils

import org.scalatest.{FunSpec, Matchers}

import scala.util.{Failure, Random, Success}

class ImplicitUtilsSuite extends FunSpec with Matchers {

  import Implicits._

  val rng = new Random(0)
  val ints = (0 until 1000).map(_ => rng.nextInt(Int.MaxValue))

  describe("traversable implicits") {

    it("finds the top and bottom k elements in a seq") {
      val k = 22
      val topK = ints.topK(k).toVector
      val botK = ints.bottomK(k).toVector
      topK.sorted.reverse shouldBe ints.sorted.reverse.take(k)
      botK.sorted shouldBe ints.sorted.take(k)
    }

    it("finds the top and bottom k elements with a by function") {
      val tuples: Seq[(String, Int)] = ints.map(i => i.toString -> i)
      val k = 22
      val topK = tuples.topK(k, _._2).toVector
      val botK = tuples.bottomK(k, _._2).toVector
      topK.sortBy(_._2).reverse shouldBe tuples.sortBy(_._2).reverse.take(k)
      botK.sortBy(_._2) shouldBe tuples.sortBy(_._2).take(k)
    }

    it("computes the intersection given an empty traversable") {
      (0 to 100).sortedIntersectionCount(Seq.empty[Int]) shouldBe Success(0)
      Seq.empty[Int].sortedIntersectionCount(0 to 100) shouldBe Success(0)
      Seq.empty[Int].sortedIntersectionCount(Seq.empty[Int]) shouldBe Success(0)
    }

    it("computes the intersection of random sorted traversables") {
      for (_ <- 0 until 100) {
        val xs = (0 to rng.nextInt(50)).map(_ => rng.nextInt(1000)).sorted
        val ys = (0 to rng.nextInt(50)).map(_ => rng.nextInt(1000)).sorted
        xs.sortedIntersectionCount(ys) shouldBe Success(xs.intersect(ys).size)
      }
    }

    it("fails to compute intersection of two unsorted traversables") {
      for (_ <- 0 until 100) {
        val xs = (0 to rng.nextInt(50)).map(_ => rng.nextInt(1000)) ++ Seq(Int.MinValue)
        val ys = (0 to rng.nextInt(50)).map(_ => rng.nextInt(1000)) ++ Seq(Int.MaxValue)
        xs.sortedIntersectionCount(ys) should matchPattern {
          case Failure(_) =>
        }
      }
    }

  }

}
