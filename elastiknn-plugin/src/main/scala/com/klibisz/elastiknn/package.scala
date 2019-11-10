package com.klibisz

import org.elasticsearch.client.Client

package object elastiknn {

  // TODO: This is bad, but some places (like the queries) require a client and it seems otherwise impossible to access it.
  private[elastiknn] object SharedClient {
    private var clientOpt: Option[Client] = None
    def set(client: Client): Unit = synchronized(this.clientOpt = Some(client))
    def client: Client = clientOpt.getOrElse(throw new IllegalStateException("Client hasn't been set yet"))
  }

}
