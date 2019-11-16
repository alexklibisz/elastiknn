package com.klibisz.elastiknn

import com.fasterxml.jackson.annotation.JsonProperty
import com.sksamuel.elastic4s.{ElasticError, ElasticRequest, Handler, HttpResponse, ResponseHandler}

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

}
