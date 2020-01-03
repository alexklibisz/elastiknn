package com.klibisz.elastiknn.processor

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.client.ElastiKnnDsl._
import org.scalatest.{AsyncFunSuite, Matchers}

class IngestProcessorSuite extends AsyncFunSuite with Matchers with Elastic4sMatchers with ElasticAsyncClient {

  test("make an exact pipeline") {
    val opts = ProcessorOptions("a", 32, ModelOptions.Exact(ExactModelOptions(Similarity.SIMILARITY_ANGULAR)))
    val req = PutPipelineRequest("exact", "exact knn pipeline", Processor("elastiknn", opts))
    for {
      res <- client.execute(req)
    } yield {
      res.shouldBeSuccess
      res.result.acknowledged shouldBe true
    }
  }

  test("make an lsh pipeline") {
    val opts = ProcessorOptions("a", 32, ModelOptions.Jaccard(JaccardLshOptions(fieldProcessed="vec_proc")))
    val req = PutPipelineRequest("lsh", "d", Processor("elastiknn", opts))
    for {
      res <- client.execute(req)
    } yield {
      res.shouldBeSuccess
      res.result.acknowledged shouldBe true
    }
  }

}
