package com.klibisz.elastiknn.models

import java.util.Random

import com.klibisz.elastiknn.api._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class HammingLshModelSuite extends AnyFunSuite with Matchers {

  test("correct number of hashes when L * k < dims") {
    new util.Random()
    val vec = Vec.SparseBool.random(10000)(new util.Random(0))
    val model = new HammingLshModel(vec.dims, 10, 3, new Random(0))
    val hashes = model.hash(vec.trueIndices, vec.totalIndices)
    hashes should have length 10
  }

  test("correct number of hashes when L * k >= dims") {
    val vec = Vec.SparseBool.random(200)(new util.Random(0))
    val model = new HammingLshModel(vec.dims, 70, 4, new Random(0))
    val hashes = model.hash(vec.trueIndices, vec.totalIndices)
    hashes should have length 70
  }

}
