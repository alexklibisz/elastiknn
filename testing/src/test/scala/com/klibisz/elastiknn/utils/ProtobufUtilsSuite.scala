package com.klibisz.elastiknn.utils

import com.google.protobuf.ByteString
import com.klibisz.elastiknn.Distance.DISTANCE_ANGULAR
import com.klibisz.elastiknn.KNearestNeighborsQuery.ExactQueryOptions
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions.Lsh
import com.klibisz.elastiknn.VectorType.VECTOR_TYPE_DOUBLE
import com.klibisz.elastiknn.utils.ProtobufUtils._
import com.klibisz.elastiknn.{KNearestNeighborsQuery, LshModelOptions, ProcessorOptions}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.JavaConverters._

class ProtobufUtilsSuite extends FunSuite with Matchers {

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

  test("query serialization and deserialization") {
    val knnq1 = KNearestNeighborsQuery(KNearestNeighborsQuery.QueryOptions.Exact(ExactQueryOptions("vecRaw", DISTANCE_ANGULAR)))
    val s1 = knnq1.toByteString.toStringUtf8
    val knnq2 = KNearestNeighborsQuery.parseFrom(ByteString.copyFromUtf8(s1).toByteArray)
    knnq2 shouldBe knnq1
  }


}
