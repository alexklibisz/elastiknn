package com.klibisz.elastiknn.rest

import com.klibisz.elastiknn.{ELASTIKNN_NAME, ENDPOINT_PREFIX, StoredScripts}
import org.apache.logging.log4j.{LogManager, Logger}
import org.elasticsearch.action.ActionResponse
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptAction
import org.elasticsearch.action.support.master.AcknowledgedResponse
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest._
import org.elasticsearch.rest.action.RestActionListener

/**
  * Endpoint which handles setup tasks. Ideally these would happen in the plugin installation itself, but for some reason
  * the createComponents method works differently when running vs. when testing, and an endpoint works fine either way.
  */
class SetupRestAction(restController: RestController) extends BaseRestHandler {

  restController.registerHandler(RestRequest.Method.POST, s"/$ENDPOINT_PREFIX/setup", this)

  override def getName: String = s"${ELASTIKNN_NAME}_setup_action"

  override def prepareRequest(request: RestRequest, client: NodeClient): BaseRestHandler.RestChannelConsumer = {

    val logger: Logger = LogManager.getLogger(getClass)

//    // Install stored scripts.
//    Seq(StoredScripts.exactAngular).foreach { s =>
//      logger.info(s"Creating stored script ${s.id}")
//      client.execute(PutStoredScriptAction.INSTANCE, s.putRequest).actionGet(1000)
//    }

    // This is the "happy" path. If anything above this crashes, it will short-circuit and return an error response.
    channel: RestChannel =>
      client.execute(
        PutStoredScriptAction.INSTANCE,
        StoredScripts.exactAngular.putRequest,
        new RestActionListener[AcknowledgedResponse](channel) {
          override def processResponse(response: AcknowledgedResponse): Unit =
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, XContentType.JSON.mediaType, "{\"acknowledged\":true}"))
        }
      )

//      channel: RestChannel =>
//        channel.sendResponse(new BytesRestResponse(RestStatus.OK, XContentType.JSON.mediaType, "{\"acknowledged\":true}"))
  }
}
