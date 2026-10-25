package com.klibisz.elastiknn.vectors

import com.klibisz.elastiknn.api.Vec
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class PanamaFloatVectorOpsSpec extends AnyFreeSpec with Matchers {

  private val dfvo = new DefaultFloatVectorOps
  private val pfvo = new PanamaFloatVectorOps
  private val seed = System.currentTimeMillis()
  private given rng: Random = new Random(seed)
  info(s"Testing with seed $seed")

  private def compare(f1: Double, f2: Double, tolerance: Double = 0.02) = {
    if (f1 == f2) succeed
    else {
      val error: Double = (f1 - f2).abs / f1.abs.min(f2.abs)
      error shouldBe <(tolerance)
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
        // Both implementations accumulate in float, in different orders, so the absolute error is proportional to the
        // vector norms. Random vectors can have a dot product close to zero, which makes the relative error large.
        // A tolerance of 0.02 failed ~1.7% of runs; 0.5 had zero failures in 2000 simulated runs.
      } yield compare(default, panama, tolerance = 0.5)
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
