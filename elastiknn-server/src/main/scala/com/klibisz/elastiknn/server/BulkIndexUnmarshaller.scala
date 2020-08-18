package com.klibisz.elastiknn.server

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import com.klibisz.elastiknn.api.Vec
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, parser}

import scala.concurrent.{ExecutionContext, Future}

class BulkIndexUnmarshaller(log: LoggingAdapter) extends FromRequestUnmarshaller[Vector[IndexRequest]] {

  import BulkIndexUnmarshaller._

  override def apply(req: HttpRequest)(implicit ec: ExecutionContext, mat: Materializer): Future[Vector[IndexRequest]] = {
    Unmarshaller
      .stringUnmarshaller(req.entity)
      .map { body =>
        body
          .split("\n")
          .grouped(2)
          .map { arr =>
            val parsedEither = for {
              inst <- parser.decode[IndexInstruction](arr.head)
              item <- parser.decode[IndexItem](arr.last)
            } yield IndexRequest(inst.index._index, inst.index._id, item.vec)
            parsedEither.left.map(e => (arr, e))
          }
          .flatMap {
            case Left((arr, err)) =>
              log.warning(s"Skipping lines [${arr.mkString(",")}] due to parsing error [${err.getMessage}]")
              None
            case Right(value) => Some(value)
          }
          .toVector
      }
  }
}

object BulkIndexUnmarshaller {
  import com.klibisz.elastiknn.api.ElasticsearchCodec._
  private case class IndexLocation(_index: String, _id: String)
  private case class IndexInstruction(index: IndexLocation)
  private case class IndexItem(vec: Vec)
  private implicit def indexLocationDec: Decoder[IndexLocation] = deriveDecoder[IndexLocation]
  private implicit def indexInstructionDec: Decoder[IndexInstruction] = deriveDecoder[IndexInstruction]
  private implicit def indexItemDec: Decoder[IndexItem] = deriveDecoder[IndexItem]
}
