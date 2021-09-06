package com.elastiknn.annb

import com.typesafe.config.ConfigFactory

import java.nio.file.Path

case class AppConfig(datasetsPath: Path, indexPath: Path, resultsPath: Path)

object AppConfig {
  def typesafe: AppConfig = {
    val c = ConfigFactory.load()
    AppConfig(
      Path.of(c.getString("elastiknn.annb.storage.datasets")).toAbsolutePath,
      Path.of(c.getString("elastiknn.annb.storage.index")).toAbsolutePath,
      Path.of(c.getString("elastiknn.annb.storage.results")).toAbsolutePath
    )
  }
}
