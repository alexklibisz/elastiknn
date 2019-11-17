package com.klibisz.elastiknn.utils

import com.klibisz.elastiknn.Distance.DISTANCE_ANGULAR
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions.Lsh
import com.klibisz.elastiknn.utils.ProtobufUtils._
import com.klibisz.elastiknn.{LshModelOptions, ProcessorOptions}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.JavaConverters._

class ProtobufUtilsTests extends FunSuite with Matchers {

  test("converting a pb message to a java map") {

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

    procOptActual shouldBe procOptExpected

  }


}
