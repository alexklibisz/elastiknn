package com.klibisz.elastiknn.elastic4s

import com.klibisz.elastiknn.KNearestNeighborsQuery
import com.klibisz.elastiknn.KNearestNeighborsQuery.{
  ExactQueryOptions,
  IndexedQueryVector
}
import com.klibisz.elastiknn.Similarity.SIMILARITY_ANGULAR
import scalapb_circe.JsonFormat

object Dummy {

  def main(args: Array[String]): Unit = {
    val knnq = KNearestNeighborsQuery()
      .withExact(ExactQueryOptions("vec", SIMILARITY_ANGULAR))
      .withIndexed(IndexedQueryVector("the index", "vec", "99"))

    println(JsonFormat.toJsonString(knnq))
  }

}
