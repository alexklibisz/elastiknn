package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.testing.ExactSimilarityReference
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class ExactSimilarityFunctionSuite extends AnyFunSpec with Matchers with LazyLogging {

  private val reps = 1000
  private val tol = 1e-7
  private val seed = System.currentTimeMillis()
  private implicit val rng = new Random(seed)
  logger.info(s"Testing with seed $seed")

  describe("L2 Similarity") {

    it("matches reference") {
      for (_ <- 0 until reps) {
        val len = rng.nextInt(4096) + 10
        val v1 = Vec.DenseFloat.random(len)
        val v2 = Vec.DenseFloat.random(len)
        ExactSimilarityFunction.L2(v1, v2) shouldBe (ExactSimilarityReference.L2(v1, v2) +- tol)
      }
    }

    it("handles identity") {
      val v1 = Vec.DenseFloat.random(199)
      ExactSimilarityFunction.L2(v1, v1) shouldBe 1d
    }

    it("handles all zeros") {
      val v1 = Vec.DenseFloat.random(199)
      val v2 = Vec.DenseFloat(v1.values.map(_ * 0))
      ExactSimilarityFunction.L2(v1, v2) shouldBe >=(0d)
      ExactSimilarityFunction.L2(v2, v2) shouldBe 1
    }

  }

  describe("L1 Similarity") {

    it("matches reference") {
      for (_ <- 0 until reps) {
        val len = rng.nextInt(4096) + 10
        val v1 = Vec.DenseFloat.random(len)
        val v2 = Vec.DenseFloat.random(len)
        ExactSimilarityFunction.L1(v1, v2) shouldBe (ExactSimilarityReference.L1(v1, v2) +- tol)
      }
    }

    it("handles identity") {
      val v1 = Vec.DenseFloat.random(199)
      ExactSimilarityFunction.L1(v1, v1) shouldBe 1d
    }

    it("handles all zeros") {
      val v1 = Vec.DenseFloat.random(199)
      val v2 = Vec.DenseFloat(v1.values.map(_ * 0))
      ExactSimilarityFunction.L1(v1, v2) shouldBe >=(0d)
      ExactSimilarityFunction.L1(v2, v2) shouldBe 1
    }

  }

  describe("Angular Similarity") {

    it("matches reference") {
      for (_ <- 0 until reps) {
        val len = rng.nextInt(4096) + 10
        val v1 = Vec.DenseFloat.random(len)
        val v2 = Vec.DenseFloat.random(len)
        ExactSimilarityFunction.Cosine(v1, v2) shouldBe (ExactSimilarityReference.Cosine(v1, v2) +- tol)
      }
    }

    it("handles identity") {
      val v1 = Vec.DenseFloat.random(199)
      ExactSimilarityFunction.Cosine(v1, v1) shouldBe (2d +- tol)
    }

    it("handles all zeros") {
      val v1 = Vec.DenseFloat.random(199)
      val v2 = Vec.DenseFloat(v1.values.map(_ * 0))
      ExactSimilarityFunction.Cosine(v1, v2) shouldBe >=(0d)
      ExactSimilarityFunction.Cosine(v2, v2) shouldBe 2d
    }

  }

  describe("Jaccard Similarity") {

    it("matches reference") {
      for (_ <- 0 until reps) {
        val len = rng.nextInt(4096) + 10
        val bias = rng.nextDouble().min(0.05)
        val v1 = Vec.SparseBool.random(len, bias)
        val v2 = Vec.SparseBool.random(len, bias)
        ExactSimilarityFunction.Jaccard(v1, v2) shouldBe (ExactSimilarityReference.Jaccard(v1, v2) +- tol)
      }
    }

    it("handles identity") {
      val v1 = Vec.SparseBool.random(199)
      ExactSimilarityFunction.Jaccard(v1, v1) shouldBe 1d
    }

    it("handles all zeros") {
      val v1 = Vec.SparseBool.random(199)
      val v2 = v1.copy(trueIndices = Array.empty)
      ExactSimilarityFunction.Jaccard(v1, v2) shouldBe >=(0d)
      ExactSimilarityFunction.Jaccard(v2, v2) shouldBe 1
    }

  }

  describe("Hamming Similarity") {

    it("matches reference") {
      for (_ <- 0 until reps) {
        val len = rng.nextInt(4096) + 10
        val bias = rng.nextDouble().min(0.05)
        val v1 = Vec.SparseBool.random(len, bias)
        val v2 = Vec.SparseBool.random(len, bias)
        ExactSimilarityFunction.Hamming(v1, v2) shouldBe (ExactSimilarityReference.Hamming(v1, v2) +- tol)
      }
    }

    it("handles identity") {
      val v1 = Vec.SparseBool.random(199)
      ExactSimilarityFunction.Hamming(v1, v1) shouldBe 1d
    }

    it("handles all zeros") {
      val v1 = Vec.SparseBool.random(199)
      val v2 = v1.copy(trueIndices = Array.empty)
      ExactSimilarityFunction.Hamming(v1, v2) shouldBe >=(0d)
      ExactSimilarityFunction.Hamming(v2, v2) shouldBe 1
    }

  }

}
