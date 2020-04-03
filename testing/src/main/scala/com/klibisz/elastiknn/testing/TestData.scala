package com.klibisz.elastiknn.testing

import java.nio.file.{Files, Path}

import com.klibisz.elastiknn.api.{Similarity, Vec}
import io.circe.Decoder
import io.circe.parser.decode
import io.circe.generic.semiauto.deriveDecoder

// Needed to derive decoder.
import com.klibisz.elastiknn.api.ElasticsearchCodec._

import scala.util.Try

case class Query(vector: Vec, similarities: Vector[Float], indices: Vector[Int])
object Query {
  implicit val dec: Decoder[Query] = deriveDecoder[Query]
}

case class TestData(corpus: Vector[Vec], queries: Vector[Query])
object TestData {
  implicit val dec: Decoder[TestData] = deriveDecoder[TestData]
  def read(similarity: Similarity, dims: Int): Try[TestData] = {
    val name = s"similarity_${similarity.toString.toLowerCase}-$dims.json"
    val resource = getClass.getResource(name)
    val contents = Files.readString(Path.of(resource.toURI))
    decode[TestData](contents).toTry
  }
}
