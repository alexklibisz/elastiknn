package com.klibisz.elastiknn.testing

import com.klibisz.elastiknn.SilentMatchers
import com.klibisz.elastiknn.api.{Similarity, Vec}
import org.scalatest.{Assertions, FunSuite, Inspectors, Matchers}

class TestDataSuite extends FunSuite with Matchers with SilentMatchers with Inspectors {

  for {
    sim <- Similarity.values
    dim <- Seq(10, 128, 512)
  } test(s"parsing test data for $dim-dimensional $sim") {
    val testDataTry = TestData.read(sim, dim)
    testDataTry shouldBe 'success
    val testData = testDataTry.get
    forAll(testData.corpus.silent) {
      case Vec.SparseBool(_, totalIndices) => totalIndices shouldBe dim
      case Vec.DenseFloat(values)          => values should have length dim
      case _                               => Assertions.fail()
    }
    forAll(testData.queries.silent) {
      _.vector match {
        case Vec.SparseBool(_, totalIndices) => totalIndices shouldBe dim
        case Vec.DenseFloat(values)          => values should have length dim
        case _                               => Assertions.fail()
      }
    }
  }

}
