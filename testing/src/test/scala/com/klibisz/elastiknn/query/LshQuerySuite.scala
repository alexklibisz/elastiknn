package com.klibisz.elastiknn.query

import java.util.UUID

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.Similarity.SIMILARITY_JACCARD
import com.klibisz.elastiknn._
import org.scalatest.{AsyncFunSuite, Inspectors, Matchers}

class LshQuerySuite
    extends AsyncFunSuite
    with QuerySuite
    with Matchers
    with SilentMatchers
    with Inspectors
    with Elastic4sMatchers
    with ElasticAsyncClient {

  private val fieldRaw = "vec_raw"
  private val fieldProc = "vec_proc"

  private val simToOpts: Map[Similarity, Seq[ModelOptions]] = Map[Similarity, Seq[ModelOptions]](
    SIMILARITY_JACCARD -> Seq(
      ModelOptions.JaccardLsh(JaccardLshModelOptions(0, fieldProc, 10, 1)),
      ModelOptions.JaccardLsh(JaccardLshModelOptions(0, fieldProc, 30, 2)),
      ModelOptions.JaccardLsh(JaccardLshModelOptions(0, fieldProc, 42, 3))
    )
  ).withDefault((_: Similarity) => Seq.empty[ModelOptions])

  for {
    sim <- Similarity.values
    mopts <- simToOpts(sim)
    dim <- testDataDims
    useCache <- Seq(true, false)
  } {

    val index = s"test-${UUID.randomUUID()}"
    val pipelineId = s"$index-pipeline"
    val qopts = JaccardLshQueryOptions(20)
    val support = new Harness(sim, fieldRaw, dim, index, pipelineId, mopts)

    test(s"search given: $index, $mopts, $dim, $qopts, $useCache") {
      support.testGiven(qopts, useCache) { queriesAndResponses =>
        forAtLeast((queriesAndResponses.length * 0.7).floor.toInt, queriesAndResponses.silent) {
          case (query, res) =>
            res.hits.hits should not be empty
            val correctCorpusIds = query.indices.map(support.corpusId).toSet
            val returnedCorpusIds = res.hits.hits.map(_.id).toSet
            correctCorpusIds.intersect(returnedCorpusIds) should not be empty
        }
      }
    }

    test(s"search indexed: $index, $mopts, $dim, $qopts, $useCache") {
      support.testIndexed(qopts, useCache) { queriesAndResponses =>
        forAll(queriesAndResponses.silent) {
          case (_, id, res) =>
            res.hits.hits should not be empty
            // Top hit should be the query vector itself.
            val self = res.hits.hits.find(_.id == id)
            self shouldBe defined
            self.map(_.score) shouldBe Some(res.hits.hits.map(_.score).max)
        }
      }
    }

  }

}
