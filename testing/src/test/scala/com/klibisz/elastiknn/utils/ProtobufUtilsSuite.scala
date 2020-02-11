package com.klibisz.elastiknn.utils

import com.google.protobuf.ByteString
import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, QueryOptions}
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions.Jaccard
import com.klibisz.elastiknn.Similarity.SIMILARITY_ANGULAR
import com.klibisz.elastiknn._
import org.scalatest.{FunSuite, Matchers}

import scala.collection.JavaConverters._

class ProtobufUtilsSuite extends FunSuite with Matchers with Utils {

  test("converting a pb message to a java map") {

    val procOptActual = ProcessorOptions(
      fieldRaw = "field_raw",
      dimension = 222,
      modelOptions = Jaccard(
        JaccardLshOptions(
          seed = 99L,
          fieldProcessed = "field_proc",
          numTables = 10,
          numBands = 10,
          numRows = 3
        ))
    ).asJavaMap

    val procOptExpected = Map(
      "fieldRaw" -> "field_raw",
      "dimension" -> 222,
      "exact" -> null,
      "jaccard" -> Map(
        "seed" -> 99L,
        "fieldProcessed" -> "field_proc",
        "numTables" -> 10,
        "numBands" -> 10,
        "numRows" -> 3
      ).asJava
    ).asJava

    procOptActual shouldBe procOptExpected

  }

  test("query serialization and deserialization") {
    val knnq1 = KNearestNeighborsQuery(QueryOptions.Exact(ExactQueryOptions("vecRaw", SIMILARITY_ANGULAR)))
    val s1 = knnq1.toByteString.toStringUtf8
    val knnq2 = KNearestNeighborsQuery.parseFrom(ByteString.copyFromUtf8(s1).toByteArray)
    knnq2 shouldBe knnq1
  }

}
