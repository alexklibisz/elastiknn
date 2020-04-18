package models

import com.sksamuel.elastic4s.Hit
import com.sksamuel.elastic4s.requests.indexes.PutMappingBuilderFn
import com.sksamuel.elastic4s.requests.mappings.PutMappingRequest
import com.sksamuel.elastic4s.requests.searches.{SearchBodyBuilderFn, SearchRequest}

import scala.util.Try
import io.circe.generic.auto._
import io.circe.parser._

trait SearchResult {
  def id: String
  def score: Float
}

object SearchResult {
  case class WordVector(word: String, id: String, score: Float) extends SearchResult
  case class Image(b64: String, id: String, score: Float) extends SearchResult

  object Image {
    case class Source(b64: String)
    def parseHit(hit: Hit): Try[Image] =
      for {
        Source(b64) <- decode[Source](hit.sourceAsString).toTry
      } yield Image(b64, hit.id, hit.score)
  }

  object WordVector {
    def parseHit(hit: Hit): Try[WordVector] = {
      ???
    }
  }

}

case class ExampleWithResults(example: Example, query: SearchRequest, results: Seq[SearchResult]) {
  def mappingJsonString: Try[String] = parse(PutMappingBuilderFn(example.mapping).string()).map(_.spaces2SortKeys).toTry
  def queryJsonString: Try[String] = parse(SearchBodyBuilderFn(query).string()).map(_.spaces2SortKeys).toTry
}
