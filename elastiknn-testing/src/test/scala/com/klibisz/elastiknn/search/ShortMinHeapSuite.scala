package com.klibisz.elastiknn.search

import org.scalatest.{FunSuite, Matchers}

import scala.util.Random

class ShortMinHeapSuite extends FunSuite with Matchers {

  test("Insert to full heap") {
    val h = new ShortMinHeap(3)
    h.insert(1)
    h.insert(2)
    h.insert(3)
    val ex = intercept[IllegalStateException](h.insert(4))
    ex.getMessage shouldBe "Cannot insert to full heap"
  }

  test("Remove from empty heap") {
    val h = new ShortMinHeap(3)
    val ex = intercept[IllegalStateException](h.remove())
    ex.getMessage shouldBe "Cannot remove from empty heap"
  }

  test("Peek empty heap") {
    val h = new ShortMinHeap(3)
    val ex = intercept[IllegalStateException](h.peek())
    ex.getMessage shouldBe "Cannot peek an empty heap"
  }

  test("Simple example") {
    val h = new ShortMinHeap(3)
    h.insert(10)
    h.peek() shouldBe 10
    h.insert(3)
    h.peek() shouldBe 3
    h.insert(11)
    h.peek() shouldBe 3
    h.size() shouldBe 3
    intercept[IllegalStateException](h.insert(42))
    h.remove() shouldBe 3
    h.size() shouldBe 2
    h.peek() shouldBe 10
    h.remove() shouldBe 10
    h.size() shouldBe 1
    h.peek() shouldBe 11
    h.remove() shouldBe 11
    intercept[IllegalStateException](h.remove())
    h.insert(22)
    h.peek() shouldBe 22
    h.size shouldBe 1
    h.clear()
    h.size() shouldBe 0
    intercept[IllegalStateException](h.peek())
  }

  test("Replace") {
    val h = new ShortMinHeap(3);
    h.insert(22);
    h.insert(33);
    h.replace(11) shouldBe 22
    h.peek() shouldBe 11
    h.insert(12)
    h.replace(10) shouldBe 11
    h.peek() shouldBe 10
  }

  test("Randomized") {
    implicit val rng: Random = new Random(0)
    for (_ <- 0 to 100) {
      val items: Vector[Short] = (0 to 999).map(_ => rng.nextInt(Short.MaxValue).toShort).toVector
      val h = new ShortMinHeap(items.length)
      items.foreach(h.insert)
      h.size() shouldBe items.length
      val popped: Vector[Short] = items.map(_ => h.remove())
      popped shouldBe items.sorted
    }

  }

}
