package com.elastiknn.annb

import com.typesafe.config.ConfigFactory

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.util.Try

case class AppConfig(
    datasetsPath: Path,
    indexPath: Path,
    resultsPath: Path,
    parallelism: Int,
    indexingTimeout: Duration,
    searchingTimeout: Duration
) {
  override def toString: String =
    s"AppConfig(datasetsPath=$datasetsPath, indexPath=$indexPath, resultsPath=$resultsPath, parallelism=$parallelism, indexingTimeout=$indexingTimeout, searchingTimeout=$searchingTimeout)"
}

object AppConfig {
  def typesafe: AppConfig = {
    val c = ConfigFactory.load().getConfig("elastiknn.annb")
    AppConfig(
      Path.of(c.getString("datasets-path")).toAbsolutePath,
      Path.of(c.getString("index-path")).toAbsolutePath,
      Path.of(c.getString("results-path")).toAbsolutePath,
      c.getString("parallelism") match {
        case s if Try(s.toFloat).isSuccess => (s.toFloat * Runtime.getRuntime.availableProcessors()).floor.toInt
        case s if Try(s.toInt).isSuccess   => s.toInt
        case _                             => Runtime.getRuntime.availableProcessors()
      },
      Duration(c.getDuration("indexing-timeout").toMinutes, TimeUnit.MINUTES),
      Duration(c.getDuration("searching-timeout").toMinutes, TimeUnit.MINUTES)
    )
  }
}
