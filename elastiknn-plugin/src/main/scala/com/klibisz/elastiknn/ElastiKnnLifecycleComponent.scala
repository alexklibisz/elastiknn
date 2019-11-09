package com.klibisz.elastiknn

import java.util.Collections

import org.apache.logging.log4j.{LogManager, Logger}
import org.elasticsearch.action.admin.cluster.storedscripts.{PutStoredScriptAction, PutStoredScriptRequest}
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.{ClusterChangedEvent, ClusterStateListener}
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.common.component.{Lifecycle, LifecycleComponent, LifecycleListener}
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.script.{Script, StoredScriptSource}

class ElastiKnnLifecycleComponent(client: Client, clusterService: ClusterService) extends LifecycleComponent {

  import ElastiKnnLifecycleComponent._

  private var state: Lifecycle.State = Lifecycle.State.INITIALIZED

  private val logger: Logger = LogManager.getLogger(getClass)

  override def lifecycleState(): Lifecycle.State = state

  override def addLifecycleListener(listener: LifecycleListener): Unit = ()

  override def removeLifecycleListener(listener: LifecycleListener): Unit = ()

  override def start(): Unit = {

    // Create scripts. Called inside a listener; otherwise you get an error: "initial cluster state not set yet"
    clusterService.addListener(new ClusterStateListener {
      override def clusterChanged(event: ClusterChangedEvent): Unit = {
        logger.info(s"Creating stored scripts")
        putStoredScriptRequests.foreach { req =>
          client.execute(PutStoredScriptAction.INSTANCE, req)
        }
        clusterService.removeListener(this)
      }
    })

    state = Lifecycle.State.STARTED
  }

  override def stop(): Unit = state = Lifecycle.State.STOPPED

  override def close(): Unit = state = Lifecycle.State.CLOSED
}

object ElastiKnnLifecycleComponent {

  // Stored scripts used by the plugin.
  // https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting-using.html#modules-scripting-stored-scripts
  val putStoredScriptRequests: Seq[PutStoredScriptRequest] = Seq(
    new PutStoredScriptRequest(
      "elastiknn_exact_angular",
      "score",
      new BytesArray("{}"),
      XContentType.JSON,
      new StoredScriptSource(
        "painless",
        """
          |return 0.99;
          |""".stripMargin,
        Collections.singletonMap(Script.CONTENT_TYPE_OPTION, XContentType.JSON.mediaType())
      )
    )
  )
}
