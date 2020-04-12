package com.klibisz.elastiknn.client

import com.klibisz.elastiknn.api.{ElasticsearchCodec, Mapping, NearestNeighborsQuery, Vec}
import com.sksamuel.elastic4s.{ElasticDsl, Indexes, XContentBuilder, XContentFactory}
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import com.sksamuel.elastic4s.requests.mappings.PutMappingRequest
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.queries.CustomQuery

trait ElastiknnRequests {

  def indexVec(indexName: String, fieldName: String, vec: Vec, id: Option[String] = None): IndexRequest = {
    val xcb = XContentFactory.jsonBuilder.rawField(fieldName, ElasticsearchCodec.nospaces(vec))
    IndexRequest(indexName, source = Some(xcb.string()), id = id)
  }

  def nearestNeighborsQuery(index: String, query: NearestNeighborsQuery, k: Int, fetchSource: Boolean = false): SearchRequest = {
    val json = ElasticsearchCodec.nospaces(query)
    val customQuery = new CustomQuery {
      override def buildQueryBody(): XContentBuilder = XContentFactory.jsonBuilder.rawField("elastiknn_nearest_neighbors", json)
    }
    ElasticDsl.search(index).query(customQuery).fetchSource(fetchSource).size(k)
  }

  def putMapping(index: String, field: String, mapping: Mapping): PutMappingRequest = {
    val mappingJsonString =
      s"""
         |{
         |  "properties": {
         |    "$field": ${ElasticsearchCodec.encode(mapping).spaces2}
         |  }
         |}
         |""".stripMargin
    ElasticDsl.putMapping(Indexes(index)).rawSource(mappingJsonString)
  }

}

object ElastiknnRequests extends ElastiknnRequests
