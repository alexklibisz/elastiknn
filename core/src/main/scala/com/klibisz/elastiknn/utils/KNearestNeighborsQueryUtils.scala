package com.klibisz.elastiknn.utils

import com.klibisz.elastiknn.KNearestNeighborsQuery
import scalapb.GeneratedMessageCompanion

trait KNearestNeighborsQueryUtils {

  implicit class KNearestNeighborsQueryCompanionImplicits(cmp: GeneratedMessageCompanion[KNearestNeighborsQuery]) {
    // TODO: switch to newer version of scalapb (>= 0.10.0) once scalapb circe has caught up.
    def apply(
        pipelineId: String,
        queryOptions: com.klibisz.elastiknn.KNearestNeighborsQuery.QueryOptions =
          com.klibisz.elastiknn.KNearestNeighborsQuery.QueryOptions.Empty,
        queryVector: com.klibisz.elastiknn.KNearestNeighborsQuery.QueryVector =
          com.klibisz.elastiknn.KNearestNeighborsQuery.QueryVector.Empty,
        useCache: Boolean = false
    ): KNearestNeighborsQuery = KNearestNeighborsQuery(pipelineId, useCache, queryOptions, queryVector)
  }

}

object KNearestNeighborsQueryUtils extends KNearestNeighborsQueryUtils
