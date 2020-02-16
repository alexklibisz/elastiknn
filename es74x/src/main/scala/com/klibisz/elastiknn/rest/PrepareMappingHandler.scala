package com.klibisz.elastiknn.rest

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.mapper.ElastiKnnVectorFieldMapper
import com.klibisz.elastiknn.utils.GeneratedMessageUtils
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser._
import org.elasticsearch.action.admin.indices.mapping.put.{PutMappingAction, PutMappingRequestBuilder}
import org.elasticsearch.action.support.master.AcknowledgedResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.action.RestActionListener
import org.elasticsearch.rest.{BaseRestHandler, BytesRestResponse, RestRequest, RestStatus}

/**
  * Defines the elastiknn-related mappings for a given index based on given processor options.
  */
final class PrepareMappingHandler extends BaseRestHandler with GeneratedMessageUtils {

  override def getName: String = s"${ELASTIKNN_NAME}_prepare_mapping_action"

  private case class Request(index: String, processorOptions: ProcessorOptions)

  override def prepareRequest(restReq: RestRequest, client: NodeClient): BaseRestHandler.RestChannelConsumer = {
    val request: Request = decode[Request](restReq.content.utf8ToString())(deriveDecoder[Request])
      .getOrElse(throw new IllegalArgumentException("Failed to parse request"))
    val dynamicTemplate = request.processorOptions.modelOptions match {
      case ModelOptions.Jaccard(jacc) =>
        s"""
          |{
          |  "${ELASTIKNN_NAME}_processed": {
          |    "path_match": "${jacc.fieldProcessed}.*",
          |    "mapping": {
          |      "type": "keyword"
          |    }
          |  }
          |}
          |""".stripMargin
      case _ => ""
    }
    val mapping: String =
      s"""
        |{
        |  "properties": {
        |    "${request.processorOptions.fieldRaw}": {
        |      "type": "${ElastiKnnVectorFieldMapper.CONTENT_TYPE}"
        |    }
        |  },
        |  "dynamic_templates": [ $dynamicTemplate ]
        |}
        |""".stripMargin

    val putMappingRequest = new PutMappingRequestBuilder(client, PutMappingAction.INSTANCE)
      .setIndices(request.index)
      .setType("doc") // This stupid thing seems to be necessary.
      .setSource(mapping, XContentType.JSON)
      .request()

    channel =>
      client.execute(
        PutMappingAction.INSTANCE,
        putMappingRequest,
        new RestActionListener[AcknowledgedResponse](channel) {
          override def processResponse(response: AcknowledgedResponse): Unit = channel.sendResponse(
            new BytesRestResponse(RestStatus.OK, XContentType.JSON.mediaType, "{\"acknowledged\": true}")
          )
        }
      )
  }
}
