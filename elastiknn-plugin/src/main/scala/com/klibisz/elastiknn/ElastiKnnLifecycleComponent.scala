package com.klibisz.elastiknn

import org.apache.logging.log4j.{LogManager, Logger}
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptAction
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.service.ClusterService
import org.elasticsearch.cluster.{ClusterChangedEvent, ClusterStateListener}
import org.elasticsearch.common.component.{Lifecycle, LifecycleComponent, LifecycleListener}

class ElastiKnnLifecycleComponent(client: Client, clusterService: ClusterService) extends LifecycleComponent {

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
        client.execute(PutStoredScriptAction.INSTANCE, StoredScripts.exactAngular)
        clusterService.removeListener(this)
      }
    })

    state = Lifecycle.State.STARTED
  }

  override def stop(): Unit = state = Lifecycle.State.STOPPED

  override def close(): Unit = state = Lifecycle.State.CLOSED
}
