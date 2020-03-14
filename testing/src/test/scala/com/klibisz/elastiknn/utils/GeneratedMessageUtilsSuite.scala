package com.klibisz.elastiknn.utils

import com.google.protobuf.ByteString
import com.klibisz.elastiknn.KNearestNeighborsQuery._
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions.JaccardLsh
import com.klibisz.elastiknn._
import org.scalatest.{FunSuite, Matchers}

import scala.collection.JavaConverters._

class GeneratedMessageUtilsSuite extends FunSuite with Matchers with Utils {

  test("converting a pb message to a java map") {

    val procOptActual = ProcessorOptions(
      fieldRaw = "field_raw",
      dimension = 222,
      modelOptions = JaccardLsh(
        JaccardLshModelOptions(
          seed = 99L,
          fieldProcessed = "field_proc",
          numBands = 10,
          numRows = 3
        ))
    ).asJavaMap

    val procOptExpected = Map(
      "fieldRaw" -> "field_raw",
      "dimension" -> 222,
      "exactComputed" -> null,
      "jaccardIndexed" -> null,
      "jaccardLsh" -> Map(
        "seed" -> 99L,
        "fieldProcessed" -> "field_proc",
        "numBands" -> 10,
        "numRows" -> 3
      ).asJava
    ).asJava

    procOptActual shouldBe procOptExpected

  }

  test("query serialization and deserialization") {
    val knnq1 =
      KNearestNeighborsQuery(pipelineId = "foo", useCache = true, QueryOptions.ExactComputed(ExactComputedQueryOptions()))
    val s1 = knnq1.toByteString.toStringUtf8
    val knnq2 = KNearestNeighborsQuery.parseFrom(ByteString.copyFromUtf8(s1).toByteArray)
    knnq2 shouldBe knnq1
  }

}
