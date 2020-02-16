package com.klibisz.elastiknn.rest

import com.klibisz.elastiknn._
import org.elasticsearch.action.admin.indices.mapping.put.{PutMappingAction, PutMappingRequest}
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.rest.{BaseRestHandler, RestChannel, RestRequest}

final class PrepareMappingHandler extends BaseRestHandler {

  override def getName: String = s"${ELASTIKNN_NAME}_prepare_mapping_action"

  override def prepareRequest(request: RestRequest, client: NodeClient): BaseRestHandler.RestChannelConsumer = {
    val mappingRequest = new PutMappingRequest()
    _: RestChannel =>
      client.execute(PutMappingAction.INSTANCE, mappingRequest)
  }
}
