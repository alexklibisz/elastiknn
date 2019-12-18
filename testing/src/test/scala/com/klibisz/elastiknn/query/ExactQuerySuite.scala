package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, QueryOptions}
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.client.ElastiKnnClient
import com.sksamuel.elastic4s.ElasticDsl
import io.circe.parser.decode
import org.scalatest._

import scala.concurrent.Future
import scala.io.BufferedSource
import scala.util.Try

/**
  * Tests for the exact query functionality, using test data generated via Python and scikit-learn.
  */
class ExactQuerySuite
    extends AsyncFunSuite
    with QuerySuite
    with Matchers
    with SilentMatchers
    with Inspectors
    with Elastic4sMatchers
    with ElasticAsyncClient
    with ElasticDsl {

  private val eknn: ElastiKnnClient = new ElastiKnnClient()

  private def readTestData(resourceName: String): Try[TestData] =
    for {
      rawJson <- Try {
        val src: BufferedSource = scala.io.Source.fromResource(resourceName)
        try src.mkString
        finally src.close()
      }
      decoded <- decode[TestData](rawJson).toTry
    } yield decoded

  test("parses test data") {
    for (testData <- Future.fromTry(readTestData("similarity_angular-10.json")))
      yield {
        forAll(testData.corpus.silent) {
          _.getFloatVector.values should have length 10
        }
        forAll(testData.queries.silent) {
          _.vector.getFloatVector.values should have length 10
        }
      }
  }

  for {
    sim <- Similarity.values
    dim <- Seq(10, 128, 512)
  } yield {
    val support = new Support("vec_raw", sim, dim, ModelOptions.Exact(ExactModelOptions(sim)))
    test(s"exact $dim-dimensional ${sim.name} search given a vector") {
      support.testGiven(QueryOptions.Exact(ExactQueryOptions("vec_raw", sim))) { queriesAndResults =>
        forAll(queriesAndResults.silent) {
          case (query, res) =>
            res.hits.hits should have length query.similarities.length
            // Just check the similarity scores. Some vectors will have the same scores, so checking indexes is brittle.
            forAll(query.similarities.zip(res.hits.hits.map(_.score)).silent) {
              case (sim, score) => score shouldBe sim +- 1e-6f
            }
        }
      }
    }
    test(s"exact $dim-dimensional ${sim.name} search with an indexed vector") {
      support.testIndexed(QueryOptions.Exact(ExactQueryOptions("vec_raw", sim))) { queriesAndResults =>
        forAll(queriesAndResults.silent) {
          case (query, id, res) =>
            val hits = res.hits.hits
            hits.length shouldBe >=(query.similarities.length)
            // The top hit should be the query vector itself.
            val self = hits.find(_.id == id)
            self shouldBe defined
            self.map(_.score) shouldBe Some(hits.map(_.score).max)

            // The remaining hits have the right scores. Only consider the corpus vectors.
            val scores = hits.filter(_.id.startsWith(support.corpusVectorIdPrefix)).map(_.score).take(query.similarities.length)
            forAll(query.similarities.zip(scores).silent) {
              case (sim, score) => score shouldBe sim +- 1e-6f
            }
        }
      }
    }
  }

}
