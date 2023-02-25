package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.vectors.PanamaFloatVectorOps
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class ExactSimilarityFunctionSuite extends AnyFunSpec with Matchers {

  private val reps = 1000
  private val tol = 1e-7
  private val seed = System.currentTimeMillis()
  private implicit val rng = new Random(seed)
  info(s"Testing with seed $seed")

  describe("L2 Similarity") {

    val l2 = new ExactSimilarityFunction.L2(new PanamaFloatVectorOps)

    it("matches reference") {
      for (_ <- 0 until reps) {
        val len = rng.nextInt(4096) + 10
        val v1 = Vec.DenseFloat.random(len)
        val v2 = Vec.DenseFloat.random(len)
        l2(v1, v2) shouldBe (ExactSimilarityReference.L2(v1, v2) +- tol)
      }
    }

    it("handles identity") {
      val v1 = Vec.DenseFloat.random(199)
      l2(v1, v1) shouldBe 1d
    }

    it("handles all zeros") {
      val v1 = Vec.DenseFloat.random(199)
      val v2 = Vec.DenseFloat(v1.values.map(_ * 0))
      l2(v1, v2) shouldBe >=(0d)
      l2(v2, v2) shouldBe 1
    }

  }

  describe("L1 Similarity") {

    val l1 = new ExactSimilarityFunction.L1(new PanamaFloatVectorOps)

    it("matches reference") {
      for (_ <- 0 until reps) {
        val len = rng.nextInt(4096) + 10
        val v1 = Vec.DenseFloat.random(len)
        val v2 = Vec.DenseFloat.random(len)
        l1(v1, v2) shouldBe (ExactSimilarityReference.L1(v1, v2) +- tol)
      }
    }

    it("handles identity") {
      val v1 = Vec.DenseFloat.random(199)
      l1(v1, v1) shouldBe 1d
    }

    it("handles all zeros") {
      val v1 = Vec.DenseFloat.random(199)
      val v2 = Vec.DenseFloat(v1.values.map(_ * 0))
      l1(v1, v2) shouldBe >=(0d)
      l1(v2, v2) shouldBe 1
    }

  }

  describe("Cosine Similarity") {

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
