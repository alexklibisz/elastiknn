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
import scala.concurrent.Future

@ThreadLeakScope(ThreadLeakScope.Scope.NONE) // https://discuss.elastic.co/t/integration-testing-in-java-carrotsearch-thread-leaks-severe-errors/26831/4
class ElastiKnnClusterIT extends ESIntegTestCase with TestingMixins {

  private lazy val client: ElasticClient = elasticClient(ESIntegTestCase.getRestClient)

  override def nodePlugins(): util.Collection[Class[_ <: Plugin]] =
    Collections.singletonList(classOf[ElastiKnnPlugin])

  def testPluginsInstalled(): Unit = await {
    client.execute(catPlugins()).map { res =>
      assertEquals(res.result.length, 1)
      assertEquals(res.result.head.component, Constants.name)
    }
  }

  def testMakeExactPipeline(): Unit = await {
    val opts = ProcessorOptions("a", "b", 32, DISTANCE_L2, Exact(ExactModel()))
    val req = PipelineRequest("exact", Pipeline("d", Seq(Processor("elastiknn", opts))))
    client.execute(req).map { res =>
      assertTrue(res.isSuccess)
      assertTrue(res.result.acknowledged)
    }
  }

  def testMakeLshPipeline(): Unit = await {
    val opts = ProcessorOptions("a", "b", 32, DISTANCE_L2, Lsh(LSHModel(k = 10, l = 20)))
    val req = PipelineRequest("lsh", Pipeline("d", Seq(Processor("elastiknn", opts))))
    client.execute(req).map { res =>
      assertTrue(res.isSuccess)
      assertTrue(res.result.acknowledged)
    }
  }

  def testExactAllDistances(): Unit = await {

    lazy val testFutures = for {
      dist <- Distance.values
      opts = ProcessorOptions("a", "b", 32, dist, Exact(ExactModel()))
      proc = Processor("elastiknn", opts)
      preq = PipelineRequest(s"exact-${dist.name.toLowerCase}", Pipeline("exact", Seq(proc)))
    } yield
      for {
        pres <- client.execute(preq)
        _ = assertTrue(pres.isSuccess)
        _ = assertTrue(pres.result.acknowledged)
      } yield {
        assertTrue(pres.result.acknowledged)
      }

    Future.sequence(testFutures)
  }

}
