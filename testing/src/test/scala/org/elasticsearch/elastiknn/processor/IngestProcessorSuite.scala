package org.elasticsearch.elastiknn.processor

import org.elasticsearch.elastiknn.ProcessorOptions.ModelOptions
import org.elasticsearch.elastiknn._
import org.elasticsearch.elastiknn.client.ElastiKnnDsl._
import org.elasticsearch.elastiknn.ProcessorOptions.ModelOptions
import org.elasticsearch.elastiknn.Similarity.SIMILARITY_ANGULAR
import org.scalatest.{AsyncFunSuite, Matchers}

class IngestProcessorSuite extends AsyncFunSuite with Matchers with Elastic4sMatchers with ElasticAsyncClient {

  test("make an exact pipeline") {
    val opts = ProcessorOptions("a", 32, ModelOptions.Exact(ExactModelOptions(SIMILARITY_ANGULAR)))
    val req = PutPipelineRequest("exact", "exact knn pipeline", Processor("elastiknn", opts))
    for {
      res <- client.execute(req)
    } yield {
      res.shouldBeSuccess
      res.result.acknowledged shouldBe true
    }
  }

  test("make an lsh pipeline") {
    val opts = ProcessorOptions("a", 32, ModelOptions.Jaccard(JaccardLshOptions(fieldProcessed = "vec_proc")))
    val req = PutPipelineRequest("lsh", "d", Processor("elastiknn", opts))
    for {
      res <- client.execute(req)
    } yield {
      res.shouldBeSuccess
      res.result.acknowledged shouldBe true
    }
  }

}
