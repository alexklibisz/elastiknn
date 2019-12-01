package com.klibisz.elastiknn

import com.fasterxml.jackson.annotation.JsonProperty
import com.klibisz.elastiknn.KNearestNeighborsQuery.{
  ExactQueryOptions,
  IndexedQueryVector
}
import com.sksamuel.elastic4s.ElasticDsl.indexInto
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.requests.searches.queries.funcscorer.ScriptScore
import com.sksamuel.elastic4s.requests.searches.queries.matches.MatchAllQuery
import com.sksamuel.elastic4s.requests.searches.queries.{
  CustomQuery,
  ExistsQuery,
  Query,
  QueryBuilderFn
}
import scalapb.GeneratedMessage
import scalapb_circe.JsonFormat

package object elastic4s {

  case class ElastiKnnSetupRequest(endpointPrefix: String = "_elastiknn")

  case class ElastiKnnSetupResponse(acknowledged: Boolean)

  object ElastiKnnSetupRequest {
    implicit object ElastiknnSetupRequestHandler
        extends Handler[ElastiKnnSetupRequest, ElastiKnnSetupResponse] {
      override def build(t: ElastiKnnSetupRequest): ElasticRequest =
        ElasticRequest("POST", s"${t.endpointPrefix}/setup")
    }
  }

  case class GetScriptRequest(id: String)

  case class Script(lang: String, source: String)

  case class GetScriptResponse(@JsonProperty("_id") id: String,
                               found: Boolean,
                               script: Script)

  object GetScriptRequest {
    implicit object GetScriptRequestHandler
        extends Handler[GetScriptRequest, GetScriptResponse] {
      override def build(t: GetScriptRequest): ElasticRequest =
        ElasticRequest("GET", s"_scripts/${t.id}")
    }
  }

  case class Processor(name: String, configuration: String)

  object Processor {
    def apply(name: String, configuration: GeneratedMessage): Processor =
      Processor(name, JsonFormat.toJsonString(configuration))
  }

  case class PutPipelineRequest(id: String,
                                description: String,
                                processors: Seq[Processor] = Seq.empty)

  object PutPipelineRequest {
    def apply(id: String,
              description: String,
              processor: Processor): PutPipelineRequest =
      PutPipelineRequest(id, description, Seq(processor))
  }

  case class PutPipelineResponse(acknowledged: Boolean)

  implicit object PutPipelineRequestHandler
      extends Handler[PutPipelineRequest, PutPipelineResponse] {
    private def processorToXContent(p: Processor): XContentBuilder = {
      val xcb = XContentFactory.jsonBuilder()
      xcb.rawField(p.name, XContentFactory.parse(p.configuration))
      xcb
    }

    override def build(request: PutPipelineRequest): ElasticRequest = {
      val xcb = XContentFactory.jsonBuilder()
      xcb.field("description", request.description)
      xcb.array("processors",
                request.processors.map(processorToXContent).toArray)
      xcb.endObject()
      ElasticRequest("PUT",
                     s"_ingest/pipeline/${request.id}",
                     HttpEntity(xcb.string()))
    }
  }

  def indexVector(index: String,
                  rawField: String,
                  vector: ElastiKnnVector,
                  pipeline: String): IndexRequest = {
    val xcb = XContentFactory.jsonBuilder
      .rawField(rawField, JsonFormat.toJsonString(vector))
    indexInto(index).source(xcb.string()).pipeline(pipeline)
  }

  private def knnQuery(knnq: KNearestNeighborsQuery): CustomQuery = () => {
    val json = JsonFormat.toJsonString(knnq)
    XContentFactory.jsonBuilder.rawField("elastiknn_knn", json)
  }

  def knnQuery(options: ExactQueryOptions,
               vector: IndexedQueryVector): CustomQuery =
    knnQuery(
      KNearestNeighborsQuery(
        KNearestNeighborsQuery.QueryOptions.Exact(options),
        KNearestNeighborsQuery.QueryVector.Indexed(vector)
      ))

  def knnQuery(options: ExactQueryOptions,
               vector: ElastiKnnVector): CustomQuery =
    knnQuery(
      KNearestNeighborsQuery(
        KNearestNeighborsQuery.QueryOptions.Exact(options),
        KNearestNeighborsQuery.QueryVector.Given(vector)
      ))

  def knnQuery(options: KNearestNeighborsQuery.QueryOptions,
               vector: KNearestNeighborsQuery.QueryVector): CustomQuery =
    knnQuery(KNearestNeighborsQuery(options, vector))

  def scriptScoreQuery(script: requests.script.Script,
                       filter: Option[Query] = None): CustomQuery =
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
