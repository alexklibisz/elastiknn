package com.klibisz.elastiknn.reference.json

import com.klibisz.elastiknn.api._
import io.circe.Encoder
import io.circe.syntax._

object CircePlayground {

  def printCirce[T: Encoder](o: T): Unit = println(o.asJson.spaces2)

  def printES[T: ElasticsearchCodec](o: T): Unit = println(implicitly[ElasticsearchCodec[T]].encode(o).asJson.spaces2)

  def main(args: Array[String]): Unit = {

    val mappings: Seq[Mapping] = Seq(
      Mapping.SparseBoolVector(100, Some(SparseBoolVectorModelOptions.JaccardIndexed)),
      Mapping.SparseBoolVector(100, Some(SparseBoolVectorModelOptions.JaccardLsh(99, 1)))
    )

    mappings.foreach { m: Mapping =>
      val j = implicitly[ElasticsearchCodec[Mapping]].encode(m).asJson
      println(j.spaces2)
      println(implicitly[ElasticsearchCodec[Mapping]].decodeJson(j))
      println("-" * 80)
    }

    val vectors: Seq[Vector] = Seq(
      Vector.IndexedVector("foo", "bar", "baz"),
      Vector.DenseFloatVector(Array(1, 2, 3)),
      Vector.SparseBoolVector(Array(1, 2, 3), 10)
    )

    vectors.foreach { v: Vector =>
      val j = implicitly[ElasticsearchCodec[Vector]].encode(v).asJson
      println(j.spaces2)
      println(implicitly[ElasticsearchCodec[Vector]].decodeJson(j))
      println("-" * 80)
    }

  }

}
