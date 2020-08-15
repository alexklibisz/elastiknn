package com.klibisz.elastiknn.benchmarks

import io.circe.{Encoder, Json, JsonObject}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import scalaj.http.Http
import zio._
import zio.blocking._

import scala.util.Try

trait AirtableClient {
  def appendRows[A: Encoder](rows: Seq[A]): Task[Unit]
}

object AirtableClient {

  private case class PostBody[A: Encoder](records: Seq[Record[A]])
  private object PostBody {
    implicit def encoder[A: Encoder]: Encoder[PostBody[A]] = deriveEncoder
  }

  private case class Record[A: Encoder](fields: A)
  private object Record {
    // Encodes a record such that nested objects are turned into strings, since Airtable doesn't support nesting.
    implicit def encoder[A: Encoder]: Encoder[Record[A]] = (a: Record[A]) => {
      val default = implicitly[Encoder[A]].apply(a.fields)
      val sansNesting = default.asObject.map { o =>
        Json.fromJsonObject {
          o.mapValues {
            case v if v.isObject => Json.fromString(v.noSpaces)
            case v               => v
          }
        }
      }
      Json.fromJsonObject(
        JsonObject(
          "fields" -> sansNesting.getOrElse(default)
        ))
    }
  }

  def devnull(): ULayer[Has[AirtableClient]] = ZLayer.succeed {
    new AirtableClient {
      override def appendRows[A: Encoder](rows: Seq[A]): Task[Unit] = Task.succeed(())
    }
  }

  def scalaj(url: String, token: String): ZLayer[Has[Blocking.Service], Nothing, Has[AirtableClient]] =
    ZLayer.fromService[Blocking.Service, AirtableClient] { blocking =>
      new AirtableClient {
        override def appendRows[R: Encoder](rows: Seq[R]): Task[Unit] = {
          val body = PostBody(rows.map(Record(_)))
          val req = Http(url)
            .header("content-type", "application/json")
            .header("authorization", s"Bearer $token")
            .postData(body.asJson.noSpaces)
          for {
            res <- blocking.effectBlocking(req.asString)
            _ <- Task.fromTry(Try(res.throwError))
          } yield ()
        }
      }
    }
}
