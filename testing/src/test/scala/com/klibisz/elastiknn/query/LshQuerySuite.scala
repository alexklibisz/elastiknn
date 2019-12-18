package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.KNearestNeighborsQuery.{LshQueryOptions, QueryOptions}
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

  private val simToOpts: Map[Similarity, Seq[ModelOptions]] = Map[Similarity, Seq[ModelOptions]](
    SIMILARITY_JACCARD -> Seq(ModelOptions.Jaccard(JaccardLshOptions(0, "vec_proc", 1, 10, 1)))
  ).withDefault((other: Similarity) => Seq.empty[ModelOptions])

  for {
    sim <- Similarity.values
    opt <- simToOpts(sim)
    dim <- testDataDims
  } {

    val support = new Support("vec_raw", sim, dim, opt)

//    test(s"approximate search given vector: ($dim, $sim, $opt)") {
//      support.testGiven(QueryOptions.Lsh(LshQueryOptions(support.pipelineId))) {
//        case queriesAndResponses =>
//          ???
//      }
//    }

  }

}
