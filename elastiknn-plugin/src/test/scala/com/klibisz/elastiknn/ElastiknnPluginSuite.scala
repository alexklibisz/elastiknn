package com.klibisz.elastiknn

import org.elasticsearch.common.settings.Settings
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ElastiknnPluginSuite extends AnyFunSuite with Matchers {

  test("plugin can be constructed and registers its settings") {
    // Elasticsearch validates setting properties when the settings are initialized, e.g., 9.5.0 started rejecting
    // the Deprecated property on index-scoped settings. This fails the same way the plugin would fail to load.
    val plugin = new ElastiknnPlugin(Settings.EMPTY)
    plugin.getSettings should have size 2
  }

}
