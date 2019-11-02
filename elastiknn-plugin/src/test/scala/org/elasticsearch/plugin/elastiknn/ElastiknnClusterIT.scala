package org.elasticsearch.plugin.elastiknn

import java.util
import java.util.Collections

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.http.JavaClient
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.test.ESIntegTestCase
import org.junit.Assert._
import org.junit.Before

import scala.concurrent.ExecutionContext.Implicits.global

@ThreadLeakScope(ThreadLeakScope.Scope.NONE) // https://discuss.elastic.co/t/integration-testing-in-java-carrotsearch-thread-leaks-severe-errors/26831/4
class ElastiknnClusterIT extends ESIntegTestCase with AsyncTesting {

  @Before
  override def setUp(): Unit = {
    this.ensureGreen()
    super.setUp()
  }

  private lazy val client: ElasticClient = {
    val javaClient = JavaClient.fromRestClient(ESIntegTestCase.getRestClient)
    ElasticClient(javaClient)
  }

  override def nodePlugins(): util.Collection[Class[_ <: Plugin]] =
    Collections.singletonList(classOf[ElastiKnnPlugin])

  def testPluginsInstalled(): Unit = await {
    for {
      res <- client.execute(catPlugins())
    } yield {
      assertEquals(res.result.length, 1)
      assertEquals(res.result.head.component, "elastiknn")
    }
  }

}
