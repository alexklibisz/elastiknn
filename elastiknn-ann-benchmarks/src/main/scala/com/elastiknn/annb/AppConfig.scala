package com.elastiknn.annb

import com.typesafe.config.ConfigFactory

import java.nio.file.Path
import scala.util.Try

case class AppConfig(datasetsPath: Path, indexPath: Path, resultsPath: Path, parallelism: Int) {
  override def toString: String =
    s"AppConfig(datasetsPath=$datasetsPath, indexPath=$indexPath, resultsPath=$resultsPath, parallelism=$parallelism)"
}

object AppConfig {
  def typesafe: AppConfig = {
    val c = ConfigFactory.load()
    AppConfig(
      Path.of(c.getString("elastiknn.annb.storage.datasets")).toAbsolutePath,
      Path.of(c.getString("elastiknn.annb.storage.index")).toAbsolutePath,
      Path.of(c.getString("elastiknn.annb.storage.results")).toAbsolutePath,
      c.getString("elastiknn.annb.runtime.parallelism") match {
        case s if Try(s.toFloat).isSuccess => (s.toFloat * Runtime.getRuntime.availableProcessors()).floor.toInt
        case s if Try(s.toInt).isSuccess   => s.toInt
        case _                             => Runtime.getRuntime.availableProcessors()
      }
    )
  }
}
