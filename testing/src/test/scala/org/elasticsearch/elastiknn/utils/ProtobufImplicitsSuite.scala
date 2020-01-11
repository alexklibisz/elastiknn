package org.elasticsearch.elastiknn.utils

import com.google.protobuf.ByteString
import org.elasticsearch.elastiknn.KNearestNeighborsQuery.ExactQueryOptions
import org.elasticsearch.elastiknn.ProcessorOptions.ModelOptions.Jaccard
import org.elasticsearch.elastiknn.Similarity._
import org.elasticsearch.elastiknn.utils.ProtobufImplicits._
import org.elasticsearch.elastiknn.{JaccardLshOptions, KNearestNeighborsQuery, ProcessorOptions}
import org.elasticsearch.elastiknn.KNearestNeighborsQuery
import org.elasticsearch.elastiknn.KNearestNeighborsQuery.QueryOptions
import org.elasticsearch.elastiknn.Similarity.SIMILARITY_ANGULAR
import org.scalatest.{FunSuite, Matchers}

import scala.collection.JavaConverters._

class ProtobufImplicitsSuite extends FunSuite with Matchers {

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
    val knnq2 =
      KNearestNeighborsQuery.parseFrom(ByteString.copyFromUtf8(s1).toByteArray)
    knnq2 shouldBe knnq1
  }

}
