package com.klibisz.elastiknn.client

import com.klibisz.elastiknn.api.{Mapping, NearestNeighborsQuery, Vec, XContentCodec}
import com.klibisz.elastiknn.client.Elastic4sCompatibility._
import com.sksamuel.elastic4s.json.{JacksonBuilder, XContentFactory}
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import com.sksamuel.elastic4s.requests.mappings.PutMappingRequest
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.{ElasticDsl, Indexes}

/**
  * Methods for creating Elastic4s requests for common elastiknn tasks.
  * Methods are optimized for documents containing only a vector and running searches as quickly as possible.
  * For example, we store the ID as a doc value instead of using the default stored ID which is slower to access.
  * I am open to creating less-optimized, more-convenient methods in the future.
  */
trait ElastiknnRequests {

  /**
    * Create a request for indexing a vector.
    *
    * @param index Name of the index.
    * @param vecField Field where vector is stored.
    * @param vec Vector to index.
    * @param storedIdField Field where document ID is stored.
    * @param id Document ID. Stored as the ID known by Elasticsearch, and in the document for faster retrieval.
    * @return Instance of a com.sksamuel.elastic4s.requests.indexes.IndexRequest.
    */
  def index(index: String, vecField: String, vec: Vec, storedIdField: String, id: String): IndexRequest = {
    val xcb = XContentFactory.jsonBuilder().rawField(vecField, XContentCodec.encodeUnsafeToString(vec)).field(storedIdField, id)
    IndexRequest(index, source = Some(JacksonBuilder.writeAsString(xcb.value)), id = Some(id))
  }

  /**
    * Create a request for running a nearest neighbors query.
    * Optimized for high performance, so it returns the document ID in the body.
    * Sets the preference parameter (see: https://www.elastic.co/guide/en/elasticsearch/reference/master/consistent-scoring.html)
    *  as the hash of the query for more deterministic results.
    *
    * @param index Index being searched against.
    * @param query Constructed query, containing the vector, field, etc.
    * @param k Number of results to return.
    * @param storedIdField Field containing the document ID. See ElastiknnRequests.index() method.
    * @return Instance of com.sksamuel.elastic4s.requests.searches.SearchRequest.
    */
  def nearestNeighbors(index: String, query: NearestNeighborsQuery, k: Int, storedIdField: String): SearchRequest =
    ElasticDsl
      .search(index)
      .query(query)
      .fetchSource(false)
      .storedFields("_none_")
      .docValues(Seq(storedIdField))
      .preference(query.hashCode.toString)
      .size(k)

  /**
    * Create a mapping containing a vector field and a stored ID field.
    *
    * @param index Index to which this mapping is applied.
    * @param vecField Field where vector is stored.
    * @param storedIdField Field where ID is stored.
    * @param vecMapping Mapping for the stored vector.
    * @return Instance of com.sksamuel.elastic4s.requests.mappings.PutMappingRequest.
    */
  def putMapping(index: String, vecField: String, storedIdField: String, vecMapping: Mapping): PutMappingRequest = {
    val mappingJsonString =
      s"""
         |{
         |  "properties": {
         |    "$vecField": ${XContentCodec.encodeUnsafeToString(vecMapping)},
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
