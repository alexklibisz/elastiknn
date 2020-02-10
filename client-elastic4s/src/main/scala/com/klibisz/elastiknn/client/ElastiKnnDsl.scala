package com.klibisz.elastiknn.client

import com.klibisz.elastiknn._
import com.klibisz.elastiknn.KNearestNeighborsQuery._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import com.sksamuel.elastic4s.requests.mappings.BasicField
import com.sksamuel.elastic4s.requests.script.Script
import com.sksamuel.elastic4s.requests.searches.queries.matches.MatchAllQuery
import com.sksamuel.elastic4s.requests.searches.queries.{CustomQuery, Query, QueryBuilderFn}
import scalapb.GeneratedMessage
import scalapb_circe.JsonFormat

/**
  * Request/response objects and helper methods based on the elastic4s library. Unfortunately this has to be an object,
  * otherwise you get runtime errors for Jackson decoding.
  */
object ElastiKnnDsl {

  def elastiKnnVectorField(name: String): BasicField = BasicField(name, "elastiknn_vector")

  case class Processor(name: String, configuration: String)

  object Processor {
    def apply(name: String, configuration: GeneratedMessage): Processor =
      Processor(name, JsonFormat.toJsonString(configuration))
  }

  case class PutPipelineRequest(id: String, description: String, processors: Seq[Processor] = Seq.empty)

  object PutPipelineRequest {
    def apply(id: String, description: String, processor: Processor): PutPipelineRequest =
      PutPipelineRequest(id, description, Seq(processor))
  }

  case class PutPipelineResponse(acknowledged: Boolean)

  implicit object PutPipelineRequestHandler extends Handler[PutPipelineRequest, PutPipelineResponse] {
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

  def knnQuery[O: QueryOptionsLike, V: QueryVectorLike](options: O, vector: V, useInMemoryCache: Boolean = false): CustomQuery =
    knnQuery(
      KNearestNeighborsQuery(
        implicitly[QueryOptionsLike[O]].apply(options),
        implicitly[QueryVectorLike[V]].apply(vector),
        useInMemoryCache
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
