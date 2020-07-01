package com.klibisz.elastiknn.client

import com.klibisz.elastiknn.api.{ElasticsearchCodec, Mapping, NearestNeighborsQuery, Vec}
import com.sksamuel.elastic4s.{ElasticDsl, Indexes, XContentBuilder, XContentFactory}
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import com.sksamuel.elastic4s.requests.mappings.PutMappingRequest
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.queries.CustomQuery

trait ElastiknnRequests {

  def index(index: String, vecField: String, vec: Vec, storedIdField: String, id: String): IndexRequest = {
    val xcb = XContentFactory.jsonBuilder.rawField(vecField, ElasticsearchCodec.nospaces(vec)).field(storedIdField, id)
    IndexRequest(index, source = Some(xcb.string()), id = Some(id))
  }

  def nearestNeighborsQuery(index: String,
                            query: NearestNeighborsQuery,
                            k: Int,
                            storedIdField: String,
                            fetchSource: Boolean = false,
                            preference: Option[String] = None): SearchRequest = {
    val json = ElasticsearchCodec.nospaces(query)
    val customQuery = new CustomQuery {
      override def buildQueryBody(): XContentBuilder = XContentFactory.jsonBuilder.rawField("elastiknn_nearest_neighbors", json)
    }
    val request = ElasticDsl
      .search(index)
      .query(customQuery)
      .fetchSource(fetchSource)
      .storedFields("_none_")
      .docValues(Seq(storedIdField))
      .size(k)
    // https://www.elastic.co/guide/en/elasticsearch/reference/master/consistent-scoring.html
    preference match {
      case Some(pref) => request.preference(pref)
      case None       => request
    }
  }

  def putMapping(index: String, vecField: String, storedIdField: String, vecMapping: Mapping): PutMappingRequest = {
    val mappingJsonString =
      s"""
         |{
         |  "properties": {
         |    "$vecField": ${ElasticsearchCodec.encode(vecMapping).spaces2},
         |    "$storedIdField": {
         |      "type": "keyword",
         |      "store": true
         |    }
         |  }
         |}
         |""".stripMargin
    ElasticDsl.putMapping(Indexes(index)).rawSource(mappingJsonString)
  }

}

object ElastiknnRequests extends ElastiknnRequests
