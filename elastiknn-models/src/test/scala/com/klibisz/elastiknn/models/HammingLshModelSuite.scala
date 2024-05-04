package com.klibisz.elastiknn.models

import java.util.Random

import com.klibisz.elastiknn.api._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class HammingLshModelSuite extends AnyFunSuite with Matchers {

  private given rng: util.Random = new util.Random()

  test("correct number of hashes when L * k < dims") {
    val vec = Vec.SparseBool.random(10000)
    val model = new HammingLshModel(vec.dims, 10, 3, new Random(0))
    val hashes = model.hash(vec.trueIndices, vec.totalIndices)
    hashes should have length 10
  }

  test("correct number of hashes when L * k >= dims") {
    val vec = Vec.SparseBool.random(200)
    val model = new HammingLshModel(vec.dims, 70, 4, new Random(0))
    val hashes = model.hash(vec.trueIndices, vec.totalIndices)
    hashes should have length 70
  }

}
