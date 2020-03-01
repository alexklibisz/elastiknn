package com.klibisz.elastiknn.processor

import com.klibisz.elastiknn.ProcessorOptions._
import com.klibisz.elastiknn.Similarity.SIMILARITY_ANGULAR
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.requests._
import com.klibisz.elastiknn.client.Elastic4sUtils._
import org.scalatest.{AsyncFunSuite, Matchers}

class IngestProcessorSuite extends AsyncFunSuite with Matchers with Elastic4sMatchers with ElasticAsyncClient {

  test("make an exact pipeline") {
    val opts = ProcessorOptions("a", 32, ModelOptions.ExactComputed(ExactComputedOptions(SIMILARITY_ANGULAR)))
    val req = PutPipelineRequest("exact", "exact knn pipeline", Processor("elastiknn", opts))
    for {
      res <- client.execute(req)
    } yield {
      res.shouldBeSuccess
      res.result.acknowledged shouldBe true
    }
  }

  test("make an lsh pipeline") {
    val opts = ProcessorOptions("a", 32, ModelOptions.JaccardLsh(JaccardLshOptions(fieldProcessed = "vec_proc")))
    val req = PutPipelineRequest("lsh", "d", Processor("elastiknn", opts))
    for {
      res <- client.execute(req)
    } yield {
      res.shouldBeSuccess
      res.result.acknowledged shouldBe true
    }
  }

}
