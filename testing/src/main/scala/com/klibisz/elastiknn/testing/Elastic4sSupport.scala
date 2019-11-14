package com.klibisz.elastiknn.testing

import com.sksamuel.elastic4s.{ElasticRequest, Handler}

trait Elastic4sSupport {

  case class ElastiKnnSetupRequest(endpointPrefix: String = "_elastiknn")

  case class ElastiKnnSetupResponse(acknowledged: Boolean)

  object ElastiKnnSetupRequest {
    implicit object ElastiknnSetupRequestHandler extends Handler[ElastiKnnSetupRequest, ElastiKnnSetupResponse] {
      override def build(t: ElastiKnnSetupRequest): ElasticRequest =
        ElasticRequest("POST", s"${t.endpointPrefix}/setup")
    }
  }

}

object Elastic4sSupport extends Elastic4sSupport
