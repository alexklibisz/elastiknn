package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.Similarity
import com.sksamuel.elastic4s.requests.mappings.{BasicField, MappingDefinition}
import io.circe.parser.decode
import org.scalatest.{AsyncTestSuite, Matchers}

import scala.util.Try

trait QuerySuite extends Matchers {

  this: AsyncTestSuite =>

  protected def readTestData(sim: Similarity, dim: Int): Try[TestData] =
    for {
      rawJson <- Try {
        val name = s"${sim.name.toLowerCase}-$dim.json"
        val src = scala.io.Source.fromResource(name)
        try src.mkString
        finally src.close()
      }
      dec <- decode[TestData](rawJson).toTry
    } yield dec

  def setupAndIndex(sim: Similarity, dim: Int) = {

    val tryReadData = readTestData(sim, dim)

    val index = s"test-exact-${sim.name.toLowerCase}"
    val pipeline = s"$index-pipeline"
    val rawField = "vec"
    val mapDef = MappingDefinition(Seq(BasicField(rawField, "elastiknn_vector")))

    def corpusId(i: Int): String = s"c$i"
    def queryId(i: Int): String = s"q$i"

  }

}
