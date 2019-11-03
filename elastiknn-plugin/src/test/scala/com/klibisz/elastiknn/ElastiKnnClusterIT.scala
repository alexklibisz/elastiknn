package com.klibisz.elastiknn

import java.util
import java.util.Collections

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope
import com.klibisz.elastiknn.Distance.DISTANCE_L2
import com.klibisz.elastiknn.ProcessorOptions.Model.{Exact, Lsh}
import com.klibisz.elastiknn.elastic4s._
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.test.ESIntegTestCase
import org.junit.Assert._

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

  def testMakePipeline(): Unit = await {
    val opts =
      ProcessorOptions("vraw",
                       "vproc",
                       128,
                       DISTANCE_L2,
                       Lsh(LSHModel(k = 10, l = 20)))
    val req =
      PipelineRequest(
        "test",
        Pipeline("elastiknn pipeline", Seq(Processor("elastiknn", opts))))
    for {
      res <- client.execute(req)
    } yield {
      assertTrue(true)
    }
  }

}
