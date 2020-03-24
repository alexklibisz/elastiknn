package com.klibisz.elastiknn.reference.json

import com.klibisz.elastiknn.api._
import io.circe.Encoder
import io.circe.syntax._

object CircePlayground {

  def printCirce[T: Encoder](o: T): Unit = println(o.asJson.spaces2)

  def printES[T: ElasticsearchCodec](o: T): Unit = println(implicitly[ElasticsearchCodec[T]].encode(o).asJson.spaces2)

  def main(args: Array[String]): Unit = {

    val mappings: Seq[Mapping] = Seq(
      Mapping.SparseBoolVector(100, SparseBoolVectorModelOptions.Exact),
      Mapping.SparseBoolVector(100, SparseBoolVectorModelOptions.JaccardIndexed),
      Mapping.SparseBoolVector(100, SparseBoolVectorModelOptions.JaccardLsh(99, 1))
    )

    mappings.foreach { m: Mapping =>
      val j = implicitly[ElasticsearchCodec[Mapping]].encode(m).asJson
      println(j.spaces2)
      println(implicitly[ElasticsearchCodec[Mapping]].decode(j))
      println("-" * 80)
    }

  }

}
