package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.UnsafeSerialization
import org.scalatest._

class MagnitudesLshSuite extends FunSuite with Matchers {

  test("comparison to slower reference") {
    val mapping = Mapping.MagnitudesLsh(10, 4)
    val mlsh = new MagnitudesLsh(mapping)
    val vec = Vec.DenseFloat(Array(10f, -2f, 0f, 99f, 0.1f, -8f, 42f, -13f, 6f, 0.1f))
    val expected = vec.values.zipWithIndex.sortBy(_._1.abs * -1).take(mapping.k).zipWithIndex.flatMap {
      case ((_, i), rank) => (0 until (mapping.k - rank)).map(_ => if (vec.values(i) >= 0) i else -i)
    }
    val actual = mlsh(vec).map(UnsafeSerialization.readInt)
    actual shouldBe expected
  }

}
