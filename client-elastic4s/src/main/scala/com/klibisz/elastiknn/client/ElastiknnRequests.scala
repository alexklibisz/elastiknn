package com.klibisz.elastiknn.client

import com.klibisz.elastiknn.api.{ElasticsearchCodec, NearestNeighborsQuery, Vec}
import com.sksamuel.elastic4s.XContentFactory
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import com.sksamuel.elastic4s.requests.searches.queries.CustomQuery

trait ElastiknnRequests {

  def indexVec(indexName: String, fieldName: String, vec: Vec, id: Option[String] = None): IndexRequest = {
    val xcb = XContentFactory.jsonBuilder.rawField(fieldName, ElasticsearchCodec.nospaces(vec))
    IndexRequest(indexName, source = Some(xcb.string()), id = id)
  }

  def nearestNeighborsQuery(query: NearestNeighborsQuery): CustomQuery = () => {
    val json = ElasticsearchCodec.nospaces(query)
    XContentFactory.jsonBuilder.rawField("elastiknn_nearest_neighbors", json)
  }

}

object ElastiknnRequests extends ElastiknnRequests
