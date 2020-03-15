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
          Seq(
            (ModelOptions.JaccardIndexed(JaccardIndexedModelOptions(fieldProc)), QueryOptions.JaccardIndexed(JaccardIndexedQueryOptions())))
        case _ => Seq.empty
      }
    }

  for {
    sim <- Similarity.values.filter(_ == SIMILARITY_JACCARD)
    dim <- testDataDims.take(1)
    (mopts, qopts) <- simToOptions(sim)
    useCache <- Seq(true, false)
  } yield {

    val index: String = s"test-${UUID.randomUUID()}"
    val pipelineId: String = s"$index-pipeline"
    val harness = new Harness(sim, fieldRaw, dim, index, pipelineId, mopts)

    test(s"search given: $index, $mopts, $dim, $qopts, $useCache") {
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

    test(s"search indexed: $index, $mopts, $dim, $qopts, $useCache") {
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

  test("readable error message for query with bogus indexed vector") {
    val index = s"test-${UUID.randomUUID()}"
    val pipelineId = s"$index-pipeline"
    val harness =
      new Harness(SIMILARITY_JACCARD, fieldRaw, testDataDims.head, index, pipelineId, ExactComputedModelOptions(SIMILARITY_JACCARD))
    for {
      _ <- harness.populateIndex
      wrongIndex = s"$index-wrong"
      wrongIndexRes <- recoverToExceptionIf[RuntimeException](
        harness.eknn.knnQuery(index,
                              pipelineId,
                              ExactComputedQueryOptions(),
                              IndexedQueryVector(wrongIndex, fieldRaw, harness.corpusId(0)),
                              10)
      )
      wrongId = harness.corpusId(Int.MaxValue)
      wrongIdRes <- recoverToExceptionIf[RuntimeException](
        harness.eknn.knnQuery(index, pipelineId, ExactComputedQueryOptions(), IndexedQueryVector(index, fieldRaw, wrongId), 10)
      )
    } yield {
      wrongIndexRes.getMessage should include(s"index_not_found_exception no such index [$index-wrong]")
      wrongIdRes.getMessage should include(s"failed to find or parse vector index [$index] id [$wrongId] field [$fieldRaw]")
    }

  }

}
