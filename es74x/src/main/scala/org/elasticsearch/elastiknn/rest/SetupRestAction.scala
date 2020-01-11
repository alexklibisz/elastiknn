package org.elasticsearch.elastiknn.rest

import org.elasticsearch.elastiknn.processor.StoredScripts
import org.elasticsearch.elastiknn.{ELASTIKNN_NAME, ENDPOINT_PREFIX}
import org.elasticsearch.action.admin.cluster.storedscripts.{PutStoredScriptAction, PutStoredScriptRequest}
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

  private val acknowledgedResponse: BytesRestResponse =
    new BytesRestResponse(RestStatus.OK, XContentType.JSON.mediaType, "{\"acknowledged\":true}")

  override def prepareRequest(request: RestRequest, client: NodeClient): BaseRestHandler.RestChannelConsumer = {

    // TODO: generalize this.
    def execMany(reqs: Seq[PutStoredScriptRequest], channel: RestChannel): Unit = reqs match {
      case r :: tail =>
        client.execute(
          PutStoredScriptAction.INSTANCE,
          r,
          new RestActionListener[AcknowledgedResponse](channel) {
            override def processResponse(response: AcknowledgedResponse): Unit = execMany(tail, channel)
          }
        )
      case _ => channel.sendResponse(acknowledgedResponse)
    }

    channel: RestChannel =>
      execMany(StoredScripts.exactScripts.map(_.putRequest), channel)

  }
}
