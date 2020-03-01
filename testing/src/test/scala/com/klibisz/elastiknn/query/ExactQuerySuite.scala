//package com.klibisz.elastiknn.query
//
//import com.klibisz.elastiknn.KNearestNeighborsQuery._
//import com.klibisz.elastiknn.ProcessorOptions._
//import com.klibisz.elastiknn.Similarity.SIMILARITY_JACCARD
//import com.klibisz.elastiknn._
//import org.scalatest._
//
///**
//  * Tests for the exact query functionality, using test data generated via Python and scikit-learn.
//  */
//class ExactQuerySuite
//    extends AsyncFunSuite
//    with QuerySuite
//    with Matchers
//    with SilentMatchers
//    with Inspectors
//    with Elastic4sMatchers
//    with ElasticAsyncClient {
//
//  private val fieldRaw = "vec_raw"
//  private val fieldProc = "vec_proc"
//
//  for {
//    sim <- Similarity.values
//    dim <- testDataDims
//    cache <- Seq(true, false)
//  } yield {
//
//    val index: String = s"test-${sim.name.toLowerCase}-$dim"
//    val pipelineId: String = s"$index-pipeline-${System.currentTimeMillis()}"
//
//    val harness = new Harness(sim, fieldRaw, dim, index, pipelineId, ExactComputedModelOptions(sim))
//
//    test(s"$dim, $cache, given") {
//      harness.testGiven(ExactComputedQueryOptions(sim))
//    }
//
//    val support = new Support("vec_raw", sim, dim, mopts)
//
//    test(s"$dim, $mopts, $cache, given") {
//      support.testGiven(qoptsFunc, cache) { queriesAndResults =>
//        forAll(queriesAndResults.silent) {
//          case (query, res) =>
//            res.hits.hits should have length query.similarities.length
//            // Just check the similarity scores. Some vectors will have the same scores, so checking indexes is brittle.
//            forAll(query.similarities.zip(res.hits.hits.map(_.score)).silent) {
//              case (sim, score) => score shouldBe sim +- 1e-5f
//            }
//        }
//      }
//
//    }
//
//    test(s"$dim, $mopts, $cache, indexed") {
//      support.testIndexed(ExactComputedQueryOptions("vec_raw", sim), cache) { queriesAndResults =>
//        forAll(queriesAndResults.silent) {
//          case (query, id, res) =>
//            val hits = res.hits.hits
//            hits.length shouldBe >=(query.similarities.length)
//            // The top hit should be the query vector itself.
//            val self = hits.find(_.id == id)
//            self shouldBe defined
//            self.map(_.score) shouldBe Some(hits.map(_.score).max)
//
//            // The remaining hits have the right scores. Only consider the corpus vectors.
//            val scores = hits.filter(_.id.startsWith(support.corpusVectorIdPrefix)).map(_.score).take(query.similarities.length)
//            forAll(query.similarities.zip(scores).silent) {
//              case (sim, score) => score shouldBe sim +- 1e-5f
//            }
//        }
//      }
//    }
//  }
//
//}
