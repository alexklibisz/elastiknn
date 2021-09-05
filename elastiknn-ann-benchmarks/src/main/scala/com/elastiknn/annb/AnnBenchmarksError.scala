package com.elastiknn.annb

import io.circe.Json

sealed trait AnnBenchmarksError extends Exception

object AnnBenchmarksError {
  case class UnknownBuildArgsError(json: Json) extends AnnBenchmarksError
  case class JsonParsingError(error: io.circe.Error) extends AnnBenchmarksError
}
