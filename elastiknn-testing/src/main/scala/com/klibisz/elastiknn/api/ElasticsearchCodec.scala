package com.klibisz.elastiknn.api

import io.circe._

trait ElasticsearchCodec[A] extends Codec[A]

object ElasticsearchCodec {
  implicit def xcontentCirceEncoder[T: XContentCodec.Encoder]: Encoder[T] = new Encoder[T] {
    override def apply(a: T): Json = {
      val s = XContentCodec.encodeUnsafeToString(a)
      val p = io.circe.parser.parse(s)
      p.fold(throw _, identity)
    }
  }
  implicit def xcontentCirceDecoder[T: XContentCodec.Decoder] = new Decoder[T] {
    override def apply(c: HCursor): Decoder.Result[T] = {
      val s = c.value.noSpacesSortKeys
      val t = XContentCodec.decodeUnsafeFromString[T](s)
      Right(t)
    }
  }

  def encode[T: ElasticsearchCodec](t: T): Json = implicitly[ElasticsearchCodec[T]].apply(t)
  def nospaces[T: ElasticsearchCodec](t: T): String = encode(t).noSpaces
  def decode[T: ElasticsearchCodec](c: HCursor): Either[DecodingFailure, T] = implicitly[ElasticsearchCodec[T]].apply(c)
  def decodeJson[T: ElasticsearchCodec](j: Json): Either[DecodingFailure, T] = implicitly[ElasticsearchCodec[T]].decodeJson(j)
  def parse(s: String): Either[Error, Json] = io.circe.parser.parse(s)

  // Danger zone.
  def decodeGet[T: ElasticsearchCodec](c: HCursor): T = decode[T](c).toTry.get
  def decodeJsonGet[T: ElasticsearchCodec](j: Json): T = decodeJson[T](j).toTry.get
  def parseGet[T: ElasticsearchCodec](s: String): Json = parse(s).toTry.get

}
