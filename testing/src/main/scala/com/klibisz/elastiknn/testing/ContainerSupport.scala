package com.klibisz.elastiknn.testing

import com.typesafe.config.Config


trait ContainerSupport {

  final case class ContainerConfig(image: String, tag: String, pluginDir: String, httpPort: Int)
  object ContainerConfig {
    def apply(config: Config): ContainerConfig = ???
  }

  def startContainer(config: ContainerConfig): Unit = {

    ???
  }


}