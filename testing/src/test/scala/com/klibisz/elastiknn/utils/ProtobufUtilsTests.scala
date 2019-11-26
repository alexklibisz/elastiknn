package com.klibisz.elastiknn.utils

import com.klibisz.elastiknn.Distance.DISTANCE_ANGULAR
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions.Lsh
import com.klibisz.elastiknn.VectorType.VECTOR_TYPE_DOUBLE
import com.klibisz.elastiknn.utils.ProtobufUtils._
import com.klibisz.elastiknn.{LshModelOptions, ProcessorOptions}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.JavaConverters._

class ProtobufUtilsTests extends FunSuite with Matchers {

  test("converting a pb message to a java map") {

    val procOptActual = ProcessorOptions(
      fieldRaw = "field_raw",
      dimension = 222,
      vectorType = VECTOR_TYPE_DOUBLE,
      modelOptions = Lsh(
        LshModelOptions(
          seed = 99,
          distance = DISTANCE_ANGULAR,
          k = 22,
          l = 33,
          fieldProcessed = "field_proc"
        ))
    ).asJavaMap

    val procOptExpected = Map(
      "fieldRaw" -> "field_raw",
      "dimension" -> 222,
      "exact" -> null,
      "vectorType" -> VECTOR_TYPE_DOUBLE.value,
      "lsh" -> Map(
        "seed" -> 99L,
        "fieldProcessed" -> "field_proc",
        "distance" -> DISTANCE_ANGULAR.index,
        "k" -> 22,
        "l" -> 33
      ).asJava
    ).asJava

    procOptActual shouldBe procOptExpected

  }


}
