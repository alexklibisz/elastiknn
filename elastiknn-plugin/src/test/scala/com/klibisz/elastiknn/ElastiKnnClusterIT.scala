package com.klibisz.elastiknn

import java.util
import java.util.Collections

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope
import com.klibisz.elastiknn.elastiknn.Distance.DISTANCE_EUCLIDEAN
import com.klibisz.elastiknn.elastiknn.ProcessorOptions.Model.Exact
import com.klibisz.elastiknn.elastiknn.{ExactModel, ProcessorOptions}
import com.sksamuel.elastic4s.{ElasticClient, ElasticRequest, HttpEntity}
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.test.ESIntegTestCase
import org.junit.Assert._
import scalapb_circe.JsonFormat

import scala.concurrent.ExecutionContext.Implicits.global

@ThreadLeakScope(ThreadLeakScope.Scope.NONE) // https://discuss.elastic.co/t/integration-testing-in-java-carrotsearch-thread-leaks-severe-errors/26831/4
class ElastiKnnClusterIT extends ESIntegTestCase with TestingMixins {

  private lazy val client: ElasticClient = elasticClient(
    ESIntegTestCase.getRestClient)

  override def nodePlugins(): util.Collection[Class[_ <: Plugin]] =
    Collections.singletonList(classOf[ElastiKnnPlugin])

  def testPluginsInstalled(): Unit = await {
    for {
      res <- client.execute(catPlugins())
    } yield {
      assertEquals(res.result.length, 1)
      assertEquals(res.result.head.component, Constants.name)
    }
  }

  def pipelineRequest(name: String,
                      procOpts: ProcessorOptions): ElasticRequest =
    ElasticRequest("PUT",
                   s"_ingest/pipeline/$name",
                   HttpEntity(JsonFormat.toJsonString(procOpts)))

  def testMakePipeline(): Unit = await {

    val req = pipelineRequest(
      "test",
      ProcessorOptions("vec", 128, DISTANCE_EUCLIDEAN, Exact(ExactModel())))

    ???
  }

}
