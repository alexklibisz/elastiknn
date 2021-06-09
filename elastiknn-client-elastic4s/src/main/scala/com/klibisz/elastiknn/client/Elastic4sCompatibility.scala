package com.klibisz.elastiknn.client

import com.klibisz.elastiknn.api.{ElasticsearchCodec, NearestNeighborsQuery}
import com.sksamuel.elastic4s.requests.searches.queries.{Query, RawQuery}

import scala.language.implicitConversions

trait Elastic4sCompatibility {

  implicit def convertQuery(nnq: NearestNeighborsQuery): Query = nnq.toQuery

  implicit class NearestNeighborsQueryCompat(nnq: NearestNeighborsQuery) {
    def toQuery: Query = RawQuery(s"""{"elastiknn_nearest_neighbors":${ElasticsearchCodec.nospaces(nnq)}}""")
  }
}

object Elastic4sCompatibility extends Elastic4sCompatibility
