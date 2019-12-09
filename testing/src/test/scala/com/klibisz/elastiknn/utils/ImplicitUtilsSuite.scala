package com.klibisz.elastiknn.utils

import org.scalatest.{FunSpec, Matchers}

class ImplicitUtilsSuite extends FunSpec with Matchers {

  import Implicits._

  describe("traversable implicits") {

    it("finds the top and bottom k elements in a seq") {
      val ints = Seq(4, 19, -1, 22, 8, 99)
      val k = 3
      val topK = ints.topK(k).toVector
      val botK = ints.bottomK(k).toVector
      topK.sorted.reverse shouldBe ints.sorted.reverse.take(k)
      botK.sorted shouldBe ints.sorted.take(k)
    }

    it("finds the top and bottom k elements with a by function") {
      val tuples: Seq[(String, Int)] = Seq(4, 19, -1, 22, 8, 99).map(i => (i.toString, i))
      val k = 3
      val topK = tuples.topK(k, _._2).toVector
      val botK = tuples.bottomK(k, _._2).toVector
      topK.sortBy(_._2).reverse shouldBe tuples.sortBy(_._2).reverse.take(3)
      botK.sortBy(_._2) shouldBe tuples.sortBy(_._2).take(k)
    }

  }

}
