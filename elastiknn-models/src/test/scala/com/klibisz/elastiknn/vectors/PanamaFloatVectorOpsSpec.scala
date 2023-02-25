package com.klibisz.elastiknn.vectors

import com.klibisz.elastiknn.api.Vec
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class PanamaFloatVectorOpsSpec extends AnyFreeSpec with Matchers {

  private val dfvo = new DefaultFloatVectorOps
  private val pfvo = new PanamaFloatVectorOps
  private val seed = 1677358265378L // System.currentTimeMillis()
  private implicit val rng = new Random(seed)
  info(s"Testing with seed $seed")

  private def compare(f1: Double, f2: Double) = {
    if (f1 == f2) succeed
    else {
      println((f1, f2))
      val error: Double = (f1 - f2).abs / f1.abs.min(f2.abs)
      error shouldBe <(0.01)
    }
  }

  "cosineSimilarity" - {
    "at parity with DefaultFloatVectorOps" in {
      for {
        _ <- 1 to 1000
        length = rng.nextInt(4096) + 1
        unit = rng.nextBoolean()
        scale = rng.nextInt(100) + 1
        v1 = Vec.DenseFloat.random(length, unit, scale)
        v2 = Vec.DenseFloat.random(length, unit, scale)
        default = dfvo.cosineSimilarity(v1.values, v2.values)
        panama = pfvo.cosineSimilarity(v1.values, v2.values)
      } yield compare(default, panama)
    }
  }

  "dotProduct" - {
    "at parity with DefaultFloatVectorOps" in {
      for {
        _ <- 1 to 1000
        length = rng.nextInt(4096) + 1
        unit = rng.nextBoolean()
        scale = rng.nextInt(100) + 1
        v1 = Vec.DenseFloat.random(length, unit, scale)
        v2 = Vec.DenseFloat.random(length, unit, scale)
        default = dfvo.dotProduct(v1.values, v2.values)
        panama = pfvo.dotProduct(v1.values, v2.values)
      } yield compare(default, panama)
    }
  }

  "l1Distance" - {
    "at parity with DefaultFloatVectorOps" in {
      for {
        _ <- 1 to 1000
        length = rng.nextInt(4096) + 1
        unit = rng.nextBoolean()
        scale = rng.nextInt(100) + 1
        v1 = Vec.DenseFloat.random(length, unit, scale)
        v2 = Vec.DenseFloat.random(length, unit, scale)
        default = dfvo.l1Distance(v1.values, v2.values)
        panama = pfvo.l1Distance(v1.values, v2.values)
      } yield compare(default, panama)
    }
  }

  "l2Distance" - {
    "at parity with DefaultFloatVectorOps" in {
      for {
        _ <- 1 to 1000
        length = rng.nextInt(4096) + 1
        unit = rng.nextBoolean()
        scale = rng.nextInt(100) + 1
        v1 = Vec.DenseFloat.random(length, unit, scale)
        v2 = Vec.DenseFloat.random(length, unit, scale)
        default = dfvo.l2Distance(v1.values, v2.values)
        panama = pfvo.l2Distance(v1.values, v2.values)
      } yield compare(default, panama)
    }
  }
}
