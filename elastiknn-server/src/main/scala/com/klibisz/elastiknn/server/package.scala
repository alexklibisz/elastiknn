package com.klibisz.elastiknn

import com.klibisz.elastiknn.api.Vec
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

  case class IndexRequest(index: String, id: String, vec: Vec)

}
