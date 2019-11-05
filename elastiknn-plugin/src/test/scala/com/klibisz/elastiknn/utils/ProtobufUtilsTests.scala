package com.klibisz.elastiknn.utils

import com.klibisz.elastiknn.Distance.DISTANCE_ANGULAR
import com.klibisz.elastiknn.{LshModelOptions, ProcessorOptions}
import org.elasticsearch.test.ESTestCase
import org.junit.Assert._

import scala.collection.JavaConverters._

class ProtobufUtilsTests extends ESTestCase {

  // TODO: you can't debug this in Intellij unless you rename it to end with IT.

  import ProtobufUtils._

  def testMessagesGetConvertedToMaps(): Unit = {

    val actual = ProcessorOptions(
      fieldRaw = "field raw",
      fieldProcessed = "field processed",
      dimension = 222,
      distance = DISTANCE_ANGULAR,
      modelOptions = LshModelOptions(
        seed = 99,
        k = 22,
        l = 33
      )
    ).asJavaMap

    val expected = Map(
      "fieldRaw" -> "field raw",
      "fieldProcessed" -> "field processed",
      "dimension" -> 222,
      "distance" -> DISTANCE_ANGULAR.index,
      "modelOptions" -> Map(
        "exact" -> null,
        "lsh" -> Map(
          "seed" -> 99L,
          "k" -> 22,
          "l" -> 33
        ).asJava
      ).asJava
    ).asJava

    assertEquals(
      actual,
      expected
    )
  }

}
