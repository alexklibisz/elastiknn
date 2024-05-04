package com.klibisz.elastiknn.client

import com.klibisz.elastiknn.api.{NearestNeighborsQuery, XContentCodec}
import com.sksamuel.elastic4s.requests.searches.queries.{Query, RawQuery}

trait Elastic4sCompatibility {
  given convertQuery: Conversion[NearestNeighborsQuery, Query] =
    nnq => RawQuery(s"""{"elastiknn_nearest_neighbors":${XContentCodec.encodeUnsafeToString(nnq)}}""")
}

object Elastic4sCompatibility extends Elastic4sCompatibility
