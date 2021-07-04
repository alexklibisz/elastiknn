package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.storage.UnsafeSerialization
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Random

class L2LshSuite extends AnyFunSuite with Matchers {

  implicit val rng = new Random(0)

  test("produces exactly L hashes with probes = 0") {
    val vec = Vec.DenseFloat.random(10)
    val lsh = new L2LshModel(vec.dims, 11, 2, 1, new java.util.Random(0))
    lsh.hash(vec.values) should have length 11
    lsh.hash(vec.values, 0) should have length 11
  }

  test("produces exactly L * (probes + 1) hashes") {
    def maxProbesForK(k: Int): Int = math.pow(3, k).toInt - 1
    val vec = Vec.DenseFloat.random(100)
    for {
      l <- 1 to 10
      k <- 1 to 5
      lsh = new L2LshModel(vec.dims, l, k, 1, new java.util.Random(0))
      maxForK = maxProbesForK(k)
      p <- 0 to maxForK + 3
    } withClue(s"L = $l, k = $k, p = $p") {
      val hashes = lsh.hash(vec.values, p)
      hashes should have length (l * (1 + p.min(maxForK)))
      hashes.foreach(_ should not be null)
    }
  }

  test("each hash contains 4 + (k * 4) bytes") {
    // An int has 4 bytes. The first int is l, the remaining bytes are the k hash values.
    for {
      l <- 1 to 10
      k <- 1 to 10
      w <- 1 to 10
      d <- 5 to 50 by 5
    } {
      val lsh = new L2LshModel(d, l, k, w, new java.util.Random(rng.nextInt()))
      val vec = Vec.DenseFloat.random(d)
      val hashes = lsh.hash(vec.values)
      hashes.length shouldBe l
      hashes.zipWithIndex.foreach {
        case (h, i) =>
          h.hash.take(4) shouldBe UnsafeSerialization.writeInts(Array(i))
      }
      hashes.foreach(_.hash.length shouldBe (4 + k * 4))
    }
  }

  test("first L hashes are the same with and without probing") {
    val vec = Vec.DenseFloat.random(100)
    val model = new L2LshModel(vec.dims, 10, 3, 1, new java.util.Random(0))
    val hashesNoProbes = model.hash(vec.values)
    val hashesWithProbes = model.hash(vec.values, 3)
    hashesNoProbes should have length 10
    hashesWithProbes.toVector.take(10) shouldBe hashesNoProbes.toVector
  }

  test("example for debugging") {
    val lsh = new L2LshModel(4, 2, 3, 1, new java.util.Random(0))
    val vec = Vec.DenseFloat(1.1f, 2.2f, 3.3f, 4.4f)
    lsh.hash(vec.values, 4)
    Assertions.succeed
  }

}
