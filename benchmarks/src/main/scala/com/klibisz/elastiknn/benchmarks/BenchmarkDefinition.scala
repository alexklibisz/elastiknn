package com.klibisz.elastiknn.benchmarks

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

case class BenchmarkDefinition(dataset: String, shards: Seq[Double], queryParallelism: Seq[Double], spaces: Seq[ParameterSpace])
object BenchmarkDefinition {
  implicit def enc: Encoder[BenchmarkDefinition] = deriveEncoder[BenchmarkDefinition]
  implicit def dec: Decoder[BenchmarkDefinition] = deriveDecoder[BenchmarkDefinition]
}

sealed trait ParameterSpace
object ParameterSpace {
  case class Exact(similarity: String) extends ParameterSpace
  case class JaccardLSH(tables: Seq[Int], bands: Seq[Int], rows: Seq[Int]) extends ParameterSpace
  implicit def enc: Encoder[ParameterSpace] = deriveEncoder[ParameterSpace]
  implicit def dec: Decoder[ParameterSpace] = deriveDecoder[ParameterSpace]
}
