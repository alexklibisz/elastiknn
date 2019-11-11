package com.klibisz.elastiknn.utils

import com.klibisz.elastiknn.{ENDPOINT_PREFIX, ProcessorOptions}
import com.sksamuel.elastic4s.{ElasticRequest, Handler, HttpEntity}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Json, JsonObject}
import scalapb_circe.JsonFormat

object Elastic4sUtils {

  case class PipelineRequest(name: String, pipeline: Pipeline)

  case class PipelineResponse(acknowledged: Boolean) {
    implicit def decoder: Decoder[PipelineResponse] =
      deriveDecoder[PipelineResponse]
  }

  object PipelineRequest {
    implicit object PipelineRequestHandler extends Handler[PipelineRequest, PipelineResponse] {
      override def build(t: PipelineRequest): ElasticRequest = {
        // See: https://www.elastic.co/guide/en/elasticsearch/reference/current/ingest-processors.html
        val body: Json = Json.fromJsonObject(
          JsonObject(
            "description" -> Json.fromString(t.pipeline.description),
            "processors" -> Json.fromValues(t.pipeline.processors.map { p =>
              Json.fromJsonObject(
                JsonObject(p.name -> JsonFormat.toJson(p.opts))
              )
            })
          ))
        val endpoint = s"_ingest/pipeline/${t.name}"
        ElasticRequest("PUT", endpoint, HttpEntity(body.noSpaces))
      }
    }
  }

  case class Pipeline(description: String, processors: Seq[Processor])

  case class Processor(name: String, opts: ProcessorOptions)

  case class ElastiKnnSetupRequest()

  case class ElastiKnnSetupResponse(acknowledged: Boolean)

  object ElastiKnnSetupRequest {
    implicit object ElastiknnSetupRequestHandler extends Handler[ElastiKnnSetupRequest, ElastiKnnSetupResponse] {
      override def build(t: ElastiKnnSetupRequest): ElasticRequest =
        ElasticRequest("POST", s"$ENDPOINT_PREFIX/setup")
    }
  }

  case class GetStoredScriptRequest(id: String)

}
