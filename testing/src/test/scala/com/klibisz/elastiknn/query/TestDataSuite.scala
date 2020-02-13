package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.{ElastiKnnVector, Elastic4sMatchers, ElasticAsyncClient, SilentMatchers, Similarity}
import org.scalatest.{Assertions, AsyncFunSuite, Inspectors, Matchers}

import scala.concurrent.Future

class TestDataSuite extends AsyncFunSuite with QuerySuite with Matchers with SilentMatchers with Inspectors {

  for {
    sim <- Similarity.values
    dim <- testDataDims
  } test(s"parsing test data for $dim-dimensional $sim") {
    for (testData <- Future.fromTry(readTestData(sim, dim))) yield {
      forAll(testData.corpus.silent) {
        case ElastiKnnVector(ElastiKnnVector.Vector.FloatVector(fv))       => fv.values should have length dim
        case ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv)) => sbv.totalIndices shouldBe dim
        case _                                                             => Assertions.fail()
      }
      forAll(testData.queries.silent) {
        _.vector match {
          case ElastiKnnVector(ElastiKnnVector.Vector.FloatVector(fv))       => fv.values should have length dim
          case ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv)) => sbv.totalIndices shouldBe dim
          case _                                                             => Assertions.fail()
        }
      }
    }
  }

}
