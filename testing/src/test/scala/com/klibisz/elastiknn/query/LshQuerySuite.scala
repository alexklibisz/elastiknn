package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.KNearestNeighborsQuery.LshQueryOptions
import com.klibisz.elastiknn.ProcessorOptions.{ModelOptions, JaccardLshOptions}
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

  private val simToOpts: Map[Similarity, Seq[ModelOptions]] = Map[Similarity, Seq[ModelOptions]](
    SIMILARITY_JACCARD -> Seq(
      ModelOptions.JaccardLsh(JaccardLshOptions(0, "vec_proc", 10, 1)),
      ModelOptions.JaccardLsh(JaccardLshOptions(0, "vec_proc", 30, 2)),
      ModelOptions.JaccardLsh(JaccardLshOptions(0, "vec_proc", 42, 3))
    )
  ).withDefault((_: Similarity) => Seq.empty[ModelOptions])

  for {
    sim <- Similarity.values
    opt <- simToOpts(sim)
    dim <- testDataDims
    useCache <- Seq(true, false)
  } {

    val support = new Support("vec_raw", sim, dim, opt)

    test(s"$dim, $sim, $opt, $useCache, given") {
      support.testGiven(LshQueryOptions(20, support.pipelineId), useCache) { queriesAndResponses =>
        forAtLeast((queriesAndResponses.length * 0.7).floor.toInt, queriesAndResponses.silent) {
          case (query, res) =>
            res.hits.hits should not be empty
            val correctCorpusIds = query.indices.map(support.corpusId).toSet
            val returnedCorpusIds = res.hits.hits.map(_.id).toSet
            correctCorpusIds.intersect(returnedCorpusIds) should not be empty
        }
      }
    }

//    test(s"$dim, $sim, $opt, $useCache, indexed") {
//      support.testIndexed(QueryOptions.Lsh(LshQueryOptions(support.pipelineId)), useCache) { queriesAndResponses =>
//        forAll(queriesAndResponses.silent) {
//          case (_, id, res) =>
//            res.hits.hits should not be empty
//            // Top hit should be the query vector itself.
//            val self = res.hits.hits.find(_.id == id)
//            self shouldBe defined
//            self.map(_.score) shouldBe Some(res.hits.hits.map(_.score).max)
//        }
//      }
//    }

  }

}
