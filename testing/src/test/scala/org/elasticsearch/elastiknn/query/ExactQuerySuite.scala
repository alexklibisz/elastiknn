package org.elasticsearch.elastiknn.query

import org.elasticsearch.elastiknn.KNearestNeighborsQuery._
import org.elasticsearch.elastiknn.ProcessorOptions.ModelOptions
import org.elasticsearch.elastiknn.Similarity.{SIMILARITY_HAMMING, SIMILARITY_JACCARD, SIMILARITY_L1, SIMILARITY_L2}
import org.elasticsearch.elastiknn._
import org.scalatest._

import scala.concurrent.Future

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
    with ElasticAsyncClient {

  val working = Set(SIMILARITY_JACCARD, SIMILARITY_HAMMING, SIMILARITY_L1, SIMILARITY_L2)

  for {
    sim <- Similarity.values.filter(working.contains)
    dim <- Seq(10, 128, 512)
  } yield {

    test(s"parsing test data for $dim-dimensional $sim") {
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

    val support = new Support("vec_raw", sim, dim, ModelOptions.Exact(ExactModelOptions(sim)))

    test(s"exact $dim-dimensional ${sim.name} search given a vector") {
      support.testGiven(QueryOptions.Exact(ExactQueryOptions("vec_raw", sim))) { queriesAndResults =>
        forAll(queriesAndResults.silent) {
          case (query, res) =>
            res.hits.hits should have length query.similarities.length
            // Just check the similarity scores. Some vectors will have the same scores, so checking indexes is brittle.
            forAll(query.similarities.zip(res.hits.hits.map(_.score)).silent) {
              case (sim, score) => score shouldBe sim +- 1e-5f
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
              case (sim, score) => score shouldBe sim +- 1e-5f
            }
        }
      }
    }
  }

}
