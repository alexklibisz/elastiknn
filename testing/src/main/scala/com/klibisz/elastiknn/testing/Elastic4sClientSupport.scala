package com.klibisz.elastiknn.testing

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.http.JavaClient
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient

trait Elastic4sClientSupport {

  protected lazy val client: ElasticClient = {
    val rc = RestClient.builder(new HttpHost("localhost", 9200)).build()
    val jc = new JavaClient(rc)
    ElasticClient(jc)
  }

}
