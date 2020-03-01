package com.klibisz.elastiknn.models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}

sealed trait ProcessedVector
object ProcessedVector {
  final case class ExactComputed() extends ProcessedVector
  final case class ExactIndexedJaccard(numTrueIndices: Int, trueIndices: String) extends ProcessedVector
  final case class JaccardLsh(hashes: String) extends ProcessedVector
  implicit def enc: Encoder[ProcessedVector] = deriveEncoder[ProcessedVector]
  implicit def dec: Decoder[ProcessedVector] = deriveDecoder[ProcessedVector]
}
