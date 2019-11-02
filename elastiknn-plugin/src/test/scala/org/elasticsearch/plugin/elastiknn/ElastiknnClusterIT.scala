package org.elasticsearch.plugin.elastiknn

import java.util
import java.util.Collections

import org.elasticsearch.plugins.Plugin
import org.elasticsearch.test.ESIntegTestCase
import org.junit.Before

class ElastiknnClusterIT extends ESIntegTestCase {

  @Before
  override def setUp(): Unit = {
    this.ensureGreen()
    super.setUp()
  }

  override def nodePlugins(): util.Collection[Class[_ <: Plugin]] =
    Collections.singletonList(classOf[ElastiKnnPlugin])

  def testDummy(): Unit = assert(true)

}
