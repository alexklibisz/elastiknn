package com.klibisz.elastiknn.rest

import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.Similarity.SIMILARITY_JACCARD
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.mapper.ElastiKnnVectorFieldMapper
import com.klibisz.elastiknn.requests.{PrepareMappingRequest, AcknowledgedResponse => AckRes}
import com.klibisz.elastiknn.utils.GeneratedMessageUtils
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser._
import io.circe.syntax._
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

  private val ackRes: BytesRestResponse =
    new BytesRestResponse(RestStatus.OK, XContentType.JSON.mediaType, AckRes(true).asJson(deriveEncoder[AckRes]).noSpaces)

  override def prepareRequest(restReq: RestRequest, client: NodeClient): BaseRestHandler.RestChannelConsumer = {
    val request: PrepareMappingRequest = decode[PrepareMappingRequest](restReq.content.utf8ToString())(deriveDecoder[PrepareMappingRequest])
      .getOrElse(throw new IllegalArgumentException("Failed to parse request"))
    val rawProp =
      s"""
        |"${request.processorOptions.fieldRaw}": {
        |  "type": "${ElastiKnnVectorFieldMapper.CONTENT_TYPE}"
        |}
        |""".stripMargin

    val procProp = request.processorOptions.modelOptions match {

      case ModelOptions.ExactIndexed(exix) if exix.similarity == SIMILARITY_JACCARD =>
        s"""
           |"${exix.fieldProcessed}": {
           |  "properties": {
           |    "ExactIndexedJaccard": {
           |      "properties": {
           |        "numTrueIndices": {
           |          "type": "integer"
           |        },
           |        "trueIndices": {
           |          "type": "text",
           |          "similarity": "boolean",
           |          "analyzer": "whitespace"
           |        }
           |      }
           |    }
           |  }
           |}
           |""".stripMargin

      case ModelOptions.JaccardLsh(jacc) =>
        // The whitespace analyzer is necessary to prevent `too_many_clauses` warnings.
        s"""
           |"${jacc.fieldProcessed}": {
           |  "properties": {
           |    "JaccardLsh": {
           |      "properties": {
           |        "hashes": {
           |          "type": "text",
           |          "similarity": "boolean",
           |          "analyzer": "whitespace"
           |        }
           |      }
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
        |    ${Seq(rawProp, procProp).filter(_.nonEmpty).mkString(",\n")}
        |  }
        |}
        |""".stripMargin

    val putMappingRequest = new PutMappingRequestBuilder(client, PutMappingAction.INSTANCE)
      .setIndices(request.index)
      .setType(request._type) // This stupid thing seems to be necessary.
      .setSource(mapping, XContentType.JSON)
      .request()

    channel =>
      client.execute(
        PutMappingAction.INSTANCE,
        putMappingRequest,
        new RestActionListener[AcknowledgedResponse](channel) {
          override def processResponse(response: AcknowledgedResponse): Unit = channel.sendResponse(ackRes)
        }
      )
  }
}
