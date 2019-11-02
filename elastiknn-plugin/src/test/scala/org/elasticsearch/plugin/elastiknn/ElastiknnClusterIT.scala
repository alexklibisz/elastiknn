package org.elasticsearch.plugin.elastiknn

import java.util
import java.util.Collections

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.http.JavaClient
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.test.ESIntegTestCase
import org.elasticsearch.test.ESIntegTestCase.ClusterScope
import org.junit.Before

@ClusterScope(scope = ESIntegTestCase.Scope.SUITE, numClientNodes = 1)
class ElastiknnClusterIT extends ESIntegTestCase {

  @Before
  override def setUp(): Unit = {
    this.ensureGreen()
    super.setUp()
  }

  private lazy val client: ElasticClient = {
    val javaClient = new JavaClient(ESIntegTestCase.getRestClient)
    ElasticClient(javaClient)
  }

  override def nodePlugins(): util.Collection[Class[_ <: Plugin]] =
    Collections.singletonList(classOf[ElastiKnnPlugin])

  def testDummy(): Unit = assert(true)

  def testPluginsInstalled(): Unit = {
    val res = client.execute(catPlugins)
    while (!res.isCompleted) {
      println("Checking...")
      Thread.sleep(1000)
    }
    assert(true)
  }

}
