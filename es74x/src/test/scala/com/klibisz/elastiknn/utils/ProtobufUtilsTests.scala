package com.klibisz.elastiknn.utils

import com.klibisz.elastiknn.Distance.DISTANCE_ANGULAR
import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, GivenQueryVector}
import com.klibisz.elastiknn.KNearestNeighborsQuery.QueryOptions.Exact
import com.klibisz.elastiknn.KNearestNeighborsQuery.QueryVector.Given
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions.Lsh
import com.klibisz.elastiknn.{KNearestNeighborsQuery, LshModelOptions, ProcessorOptions}
import org.elasticsearch.test.ESTestCase
import org.junit.Assert._

import scala.collection.JavaConverters._

class ProtobufUtilsTests extends ESTestCase {

  // TODO: you can't debug this in Intellij unless you rename it to end with IT.

  import ProtobufUtils._

  def testMessagesGetConvertedToMaps(): Unit = {

    val procOptActual = ProcessorOptions(
      fieldRaw = "field raw",
      fieldProcessed = "field processed",
      discardRaw = true,
      dimension = 222,
      modelOptions = Lsh(
        LshModelOptions(
          seed = 99,
          distance = DISTANCE_ANGULAR,
          k = 22,
          l = 33
        ))
    ).asJavaMap

    val procOptExpected = Map(
      "fieldRaw" -> "field raw",
      "fieldProcessed" -> "field processed",
      "dimension" -> 222,
      "discardRaw" -> true,
      "exact" -> null,
      "lsh" -> Map(
        "seed" -> 99L,
        "distance" -> DISTANCE_ANGULAR.index,
        "k" -> 22,
        "l" -> 33
      ).asJava
    ).asJava

    assertEquals(procOptActual, procOptExpected)
  }

}
