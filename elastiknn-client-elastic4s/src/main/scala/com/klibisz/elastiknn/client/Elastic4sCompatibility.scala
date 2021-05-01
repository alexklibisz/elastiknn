package com.klibisz.elastiknn.client

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api.{ElasticsearchCodec, NearestNeighborsQuery}
import com.sksamuel.elastic4s.json.{XContentBuilder, XContentFactory}
import com.sksamuel.elastic4s.requests.searches.queries.{CustomQuery, Query}

import scala.language.implicitConversions

object Elastic4sCompatibility {

  implicit def convertQuery(nnq: NearestNeighborsQuery): Query = nnq.toQuery

  implicit class NearestNeighborsQueryCompat(nnq: NearestNeighborsQuery) {
    def toQuery: Query = new CustomQuery {
      override def buildQueryBody(): XContentBuilder =
        XContentFactory.jsonBuilder.rawField(s"${ELASTIKNN_NAME}_nearest_neighbors", ElasticsearchCodec.nospaces(nnq))

    }
  }

}
