package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.Vec
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class CosineLshModelSuite extends AnyFunSuite with Matchers {

  test("model is invariant to vector magnitude") {
    implicit val rng: Random = new Random(0)
    val dims = 10
    for {
      l <- 1 to 100 by 10
      k <- 1 to 5
      isUnit <- Seq(true, false)
    } {
      val mlsh = new CosineLshModel(dims, l, k, new java.util.Random(0))
      val vec = Vec.DenseFloat.random(dims, unit = isUnit)
      val scaled = (1 to 10).map(m => vec.copy(vec.values.map(_ * m)))
      val hashed = scaled.map(v => mlsh.hash(v.values).toList)
      scaled.distinct.length shouldBe 10
      hashed.distinct.length shouldBe 1
    }
  }

}
