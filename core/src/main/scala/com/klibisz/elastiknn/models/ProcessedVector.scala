package com.klibisz.elastiknn.models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}

sealed trait ProcessedVector
object ProcessedVector {
  final case class ExactComputed() extends ProcessedVector
  object ExactComputed {
    implicit def enc: Encoder[ExactComputed] = deriveEncoder
    implicit def dec: Decoder[ExactComputed] = deriveDecoder
  }

  final case class ExactIndexedJaccard(numTrueIndices: Int, trueIndices: String) extends ProcessedVector
  object ExactIndexedJaccard {
    implicit def enc: Encoder[ExactIndexedJaccard] = deriveEncoder
    implicit def dec: Decoder[ExactIndexedJaccard] = deriveDecoder
  }

  final case class JaccardLsh(hashes: String) extends ProcessedVector
  object JaccardLsh {
    implicit def enc: Encoder[JaccardLsh] = deriveEncoder
    implicit def dec: Decoder[JaccardLsh] = deriveDecoder
  }

  implicit def enc: Encoder[ProcessedVector] = deriveEncoder
  implicit def dec: Decoder[ProcessedVector] = deriveDecoder
}
