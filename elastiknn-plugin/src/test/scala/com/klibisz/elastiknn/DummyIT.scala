package com.klibisz.elastiknn

import com.klibisz.elastiknn.Distance.DISTANCE_ANGULAR
import com.klibisz.elastiknn.KNearestNeighborsQuery.{ExactQueryOptions, GivenQueryVector}
import com.klibisz.elastiknn.KNearestNeighborsQuery.QueryOptions.Exact
import com.klibisz.elastiknn.KNearestNeighborsQuery.QueryVector.Given
import org.elasticsearch.test.ESTestCase
import org.scalatest.FunSpec
import scalapb_circe.JsonFormat

class DummyIT extends FunSpec {

//  def testDummy(): Unit = {
//    val knnqActual = KNearestNeighborsQuery(
//      pipelineId = "foo",
//      k = 20,
//      queryOptions = Exact(
//        ExactQueryOptions(
//          distance = DISTANCE_ANGULAR
//        )
//      ),
//      queryVector = Given(
//        GivenQueryVector(Array(1.0f, 2.0f, 3.0f))
//      )
//    )
//
//    println(JsonFormat.toJsonString(knnqActual))
//
//  }

  describe("stuff") {
    it("Does stuff") {
      println("hi")
    }
  }

}
