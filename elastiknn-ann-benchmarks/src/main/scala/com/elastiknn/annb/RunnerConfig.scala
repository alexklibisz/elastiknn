package com.elastiknn.annb

import com.typesafe.config.ConfigFactory

import java.nio.file.Path

case class RunnerConfig(datasetsPath: Path, indexPath: Path, resultsPath: Path)

object RunnerConfig {
  def configured: RunnerConfig = {
    val c = ConfigFactory.load()
    RunnerConfig(
      Path.of(c.getString("elastiknn.annb.storage.datasets")).toAbsolutePath,
      Path.of(c.getString("elastiknn.annb.storage.index")).toAbsolutePath,
      Path.of(c.getString("elastiknn.annb.storage.results")).toAbsolutePath
    )
  }
}
