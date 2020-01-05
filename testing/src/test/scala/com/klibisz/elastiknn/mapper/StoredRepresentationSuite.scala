package com.klibisz.elastiknn.mapper

import com.klibisz.elastiknn.storage.StoredSparseBoolVector
import com.klibisz.elastiknn.SparseBoolVector
import org.scalatest.{FunSuite, Inspectors, Matchers}
import com.klibisz.elastiknn.utils.Implicits._

import scala.util.Random

class StoredRepresentationSuite extends FunSuite with Inspectors with Matchers {

  implicit val rng: Random = new Random(0)

  test("sparse bool vector serialization and contains") {
    val sbv = SparseBoolVector.random(1000)
    val ssbv1 = StoredSparseBoolVector(sbv)
    val ssbv2 = StoredSparseBoolVector.parseFrom(ssbv1.toByteArray)
    forAll(0 until sbv.totalIndices) { i =>
      ssbv1.contains(i) shouldBe sbv.trueIndices.contains(i)
      ssbv2.contains(i) shouldBe sbv.trueIndices.contains(i)
    }
  }

}
