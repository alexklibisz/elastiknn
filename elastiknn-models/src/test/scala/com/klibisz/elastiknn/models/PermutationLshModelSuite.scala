package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.ByteBufferSerialization._
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Random

class PermutationLshModelSuite extends AnyFunSuite with Matchers {

  test("example from paper") {
    val mapping = Mapping.PermutationLsh(6, 4, true)
    val mlsh = new PermutationLshModel(mapping.k, mapping.repeating)
    val vec = Vec.DenseFloat(0.1f, -0.3f, -0.4f, 0, 0.2f)
    val hashes = mlsh.hash(vec.values).map(h => (readInt(h.hash), h.freq))
    hashes shouldBe Array((-3, 4), (-2, 3), (5, 2), (1, 1))
  }

  test("example from paper without repetition") {
    val mapping = Mapping.PermutationLsh(6, 4, false)
    val mlsh = new PermutationLshModel(mapping.k, mapping.repeating)
    val vec = Vec.DenseFloat(0.1f, -0.3f, -0.4f, 0, 0.2f)
    val hashes = mlsh.hash(vec.values).map(h => (readInt(h.hash), h.freq))
    hashes shouldBe Array((-3, 1), (-2, 1), (5, 1), (1, 1))
  }

  test("another example") {
    val mapping = Mapping.PermutationLsh(10, 4, true)
    val mlsh = new PermutationLshModel(mapping.k, mapping.repeating)
    val vec = Vec.DenseFloat(10f, -2f, 0f, 99f, 0.1f, -8f, 42f, -13f, 6f, 0.1f)
    val hashes = mlsh.hash(vec.values).map(h => (readInt(h.hash), h.freq))
    // Get the top 4 indices by absolute value:   (4, 7, 8, 1)
    // Negate the ones with negative values:      (4, 7, -8, 1)
    // Repeat each one proportional to its rank:  (4, 4, 4, 4, 7, 7, 7, -8, -8, 1)
    hashes shouldBe Array((4, 4), (7, 3), (-8, 2), (1, 1))
  }

  test("ties") {
    // Since index 1 and 2 are tied, index 5 should have freq = 2 instead of 3.
    val mlsh = new PermutationLshModel(4, true)
    val vec = Vec.DenseFloat(2f, 2f, 0f, 0f, 1f, 4f)
    val hashes = mlsh.hash(vec.values).map(h => (readInt(h.hash), h.freq))
    hashes.sorted shouldBe Array((6, 4), (1, 3), (2, 3), (5, 1)).sorted
  }

  test("deterministic hashing") {
    given rng: Random = new Random(0)
    val dims = 1024
    val mlsh = new PermutationLshModel(128, true)
    (0 until 100).foreach { _ =>
      val vec = Vec.DenseFloat.random(dims)
      val hashes = (0 until 100).map(_ => mlsh.hash(vec.values).map(h => (readInt(h.hash), h.freq)).mkString(","))
      hashes.distinct.length shouldBe 1
    }
  }

  test("model is invariant to vector magnitude") {
    given rng: Random = new Random(0)
    val dims = 10
    for {
      isUnit <- Seq(true, false)
      repeating <- Seq(true, false)
    } {
      val mlsh = new PermutationLshModel(dims, repeating)
      val vec = Vec.DenseFloat.random(dims, unit = isUnit)
      val scaled = (1 to 10).map(m => vec.copy(vec.values.map(_ * m)))
      val hashed = scaled.map(v => mlsh.hash(v.values).toList)
      scaled.distinct.length shouldBe 10
      hashed.distinct.length shouldBe 1
    }
  }
}
