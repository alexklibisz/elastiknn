package com.klibisz.elastiknn

import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec

package object server {

  implicit val cfg: Configuration = Configuration.default.withSnakeCaseMemberNames

  case class Acknowledged(acknowledged: Boolean)
  object Acknowledged {
    implicit val encoder: Codec[Acknowledged] = deriveConfiguredCodec
  }

  case class CreateIndexResponse(acknowledged: Boolean, shardsAcknowledged: Boolean, index: String)
  object CreateIndexResponse {
    implicit val encoder: Codec[CreateIndexResponse] = deriveConfiguredCodec
  }

}
