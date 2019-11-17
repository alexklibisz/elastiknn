package com.klibisz.elastiknn

import com.fasterxml.jackson.annotation.JsonProperty
import com.sksamuel.elastic4s.{ElasticRequest, Handler, HttpEntity}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Json, JsonObject}
import scalapb_circe.JsonFormat

package object elastic4s {

  case class ElastiKnnSetupRequest(endpointPrefix: String = "_elastiknn")

  case class ElastiKnnSetupResponse(acknowledged: Boolean)

  object ElastiKnnSetupRequest {
    implicit object ElastiknnSetupRequestHandler extends Handler[ElastiKnnSetupRequest, ElastiKnnSetupResponse] {
      override def build(t: ElastiKnnSetupRequest): ElasticRequest =
        ElasticRequest("POST", s"${t.endpointPrefix}/setup")
    }
  }

  case class GetScriptRequest(id: String)

  case class Script(lang: String, source: String)

  case class GetScriptResponse(@JsonProperty("_id") id: String, found: Boolean, script: Script)

  object GetScriptRequest {
    implicit object GetScriptRequestHandler extends Handler[GetScriptRequest, GetScriptResponse] {
      override def build(t: GetScriptRequest): ElasticRequest = ElasticRequest("GET", s"_scripts/${t.id}")
    }
  }

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

  //  def helperVecToSource(field: String, vec: Array[Double]): Json = Json.fromJsonObject(
  //    JsonObject(field -> Json.fromValues(vec.map(Json.fromDoubleOrNull)))
  //  )

  case class IndexVectorRequest(rawField: String, vector: Array[Double], pipelineId: String)

  object IndexVectorRequest {

  }


}
