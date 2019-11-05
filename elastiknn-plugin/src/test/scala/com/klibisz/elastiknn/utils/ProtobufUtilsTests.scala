package com.klibisz.elastiknn.utils

import com.klibisz.elastiknn.Distance.DISTANCE_ANGULAR
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions.Lsh
import com.klibisz.elastiknn.{LshModelOptions, ProcessorOptions}
import org.elasticsearch.test.ESTestCase
import org.junit.Assert._

class ProtobufUtilsTests extends ESTestCase {

  import ProtobufUtils._

  def testPbMessagesGetConvertedToMaps(): Unit = {

    val msg = ProcessorOptions(
      fieldRaw = "field raw",
      fieldProcessed = "field processed",
      dimension = 222,
      distance = DISTANCE_ANGULAR,
      modelOptions = Lsh(
        LshModelOptions(
          seed = 99,
          k = 22,
          l = 33
        ))
    )

    assertEquals(
      msg.asMap,
      Map(
        "fieldRaw" -> "field raw",
        "fieldProcessed" -> "field processed",
        "dimension" -> 222,
        "distance" -> DISTANCE_ANGULAR.index,
        "lsh" -> Map(
          "seed" -> 99,
          "k" -> 22,
          "l" -> 33
        ),
        "exact" -> null
      )
    )
  }

}
