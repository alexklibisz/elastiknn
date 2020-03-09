package com.klibisz.elastiknn.query

import java.util.UUID

import com.klibisz.elastiknn.KNearestNeighborsQuery._
import com.klibisz.elastiknn.ProcessorOptions._
import com.klibisz.elastiknn.Similarity.SIMILARITY_JACCARD
import com.klibisz.elastiknn._
import org.scalatest._

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

  private val fieldRaw = "vec_raw"
  private val fieldProc = "vec_proc"

  private def simToOptions(sim: Similarity): Seq[(ModelOptions, QueryOptions)] =
    Seq((ModelOptions.ExactComputed(ExactComputedModelOptions(sim)), QueryOptions.ExactComputed(ExactComputedQueryOptions()))) ++ {
      sim match {
        case SIMILARITY_JACCARD =>
          Seq((ModelOptions.ExactIndexed(ExactIndexedModelOptions(sim, fieldProc)), QueryOptions.ExactIndexed(ExactIndexedQueryOptions())))
        case _ => Seq.empty
      }
    }

  for {
    sim <- Similarity.values
    dim <- testDataDims
    (mopts, qopts) <- simToOptions(sim)
    useCache <- Seq(true, false)
  } yield {

    val index: String = s"test-${sim.name.toLowerCase}-$dim"
    val pipelineId: String = s"$index-pipeline-${UUID.randomUUID()}"
    val harness = new Harness(sim, fieldRaw, dim, index, pipelineId, mopts)

    test(s"$mopts, $dim, given, $qopts, $useCache") {
      harness.testGiven(qopts, useCache) { qAndR =>
        forAll(qAndR.silent) {
          case (query, res) =>
            res.hits.hits should have length query.similarities.length
            // Just check the similarity scores. Some vectors will have the same scores, so checking indexes is brittle.
            forAll(query.similarities.zip(res.hits.hits.map(_.score)).silent) {
              case (sim, score) => score shouldBe sim +- 1e-5f
            }
        }
      }
    }

    test(s"$mopts, $dim, indexed, $qopts, $useCache") {
      harness.testIndexed(qopts, useCache) { qAndR =>
        forAll(qAndR.silent) {
          case (query, id, res) =>
            val hits = res.hits.hits
            hits.length shouldBe >=(query.similarities.length)
            // The top hit should be the query vector itself.
            val self = hits.find(_.id == id)
            self shouldBe defined
            self.map(_.score) shouldBe Some(hits.map(_.score).max)

            // The remaining hits have the right scores. Only consider the corpus vectors.
            val scores = hits.filter(_.id.startsWith(harness.corpusVectorIdPrefix)).map(_.score).take(query.similarities.length)
            forAll(query.similarities.zip(scores).silent) {
              case (sim, score) => score shouldBe sim +- 1e-5f
            }
        }
      }
    }
  }

}
