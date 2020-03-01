package com.klibisz.elastiknn.client

import com.klibisz.elastiknn._
import com.klibisz.elastiknn.requests.{AcknowledgedResponse, PrepareMappingRequest, Processor, PutPipelineRequest}
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import com.sksamuel.elastic4s.requests.script.Script
import com.sksamuel.elastic4s.requests.searches.queries.matches.MatchAllQuery
import com.sksamuel.elastic4s.requests.searches.queries.{CustomQuery, Query, QueryBuilderFn}
import scalapb_circe.JsonFormat

object Elastic4sUtils {

  implicit object PutPipelineRequestHandler extends Handler[PutPipelineRequest, AcknowledgedResponse] {
    private def processorToXContent(p: Processor): XContentBuilder = {
      val xcb = XContentFactory.jsonBuilder()
      xcb.rawField(p.name, XContentFactory.parse(p.configuration))
      xcb
    }

    override def build(request: PutPipelineRequest): ElasticRequest = {
      val xcb = XContentFactory.jsonBuilder()
      xcb.field("description", request.description)
      xcb.array("processors", request.processors.map(processorToXContent).toArray)
      xcb.endObject()
      ElasticRequest("PUT", s"_ingest/pipeline/${request.id}", HttpEntity(xcb.string()))
    }
  }

  implicit object PrepareMappingRequestHandler extends Handler[PrepareMappingRequest, AcknowledgedResponse] {
    override def build(t: PrepareMappingRequest): ElasticRequest = {
      val xcb = XContentFactory.jsonBuilder()
      xcb.field("index", t.index)
      xcb.rawField("processorOptions", XContentFactory.parse(JsonFormat.toJsonString(t.processorOptions)))
      xcb.field("_type", t._type)
      xcb.endObject()
      ElasticRequest("PUT", s"$ENDPOINT_PREFIX/prepare_mapping", HttpEntity(xcb.string()))
    }
  }

  def indexVector[V: ElastiKnnVectorLike](index: String,
                                          rawField: String,
                                          vector: V,
                                          id: Option[String] = None,
                                          pipeline: Option[String] = None): IndexRequest = {
    val ekv = implicitly[ElastiKnnVectorLike[V]].apply(vector)
    val xcb = XContentFactory.jsonBuilder.rawField(rawField, JsonFormat.toJsonString(ekv))
    IndexRequest(index, source = Some(xcb.string()), id = id, pipeline = pipeline)
  }

  def knnQuery(knnq: KNearestNeighborsQuery): CustomQuery =
    () => {
      val json = JsonFormat.toJsonString(knnq)
      XContentFactory.jsonBuilder.rawField("elastiknn_knn", json)
    }

  def knnQuery[O: QueryOptionsLike, V: QueryVectorLike](pipelineId: String,
                                                        options: O,
                                                        vector: V,
                                                        useInMemoryCache: Boolean = false): CustomQuery =
    knnQuery(
      KNearestNeighborsQuery(
        pipelineId,
        useInMemoryCache,
        implicitly[QueryOptionsLike[O]].apply(options),
        implicitly[QueryVectorLike[V]].apply(vector)
      )
    )

  def scriptScoreQuery(script: Script, filter: Option[Query] = None): CustomQuery =
    () =>
      XContentFactory
        .jsonBuilder()
        .startObject("script_score")
        .rawField("query", QueryBuilderFn(filter.getOrElse(MatchAllQuery())))
        .startObject("script")
        .field("source", script.script)
        .autofield("params", script.params)
        .endObject()
        .endObject()

}
