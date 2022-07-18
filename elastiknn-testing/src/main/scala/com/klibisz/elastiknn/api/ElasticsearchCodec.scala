package com.klibisz.elastiknn.api

//import io.circe.Decoder.Result
import io.circe._

trait ElasticsearchCodec[A] extends Codec[A]

object ElasticsearchCodec {
//  implicit def fromEncoderDecoder[T](implicit enc: Encoder[T], dec: Decoder[T]): ElasticsearchCodec[T] = new ElasticsearchCodec[T] {
//    override def apply(a: T): Json = enc(a)
//    override def apply(c: HCursor): Result[T] = dec(c)
//  }

  implicit def fromXContentCodecEncoder[T: XContentCodec.Encoder]: Encoder[T] = new Encoder[T] {
    override def apply(a: T): Json = {
      val s = XContentCodec.encodeUnsafeToString(a)
      val p = io.circe.parser.parse(s)
      p.fold(throw _, identity)
    }
  }
  implicit def fromXContentCodecDecoder[T: XContentCodec.Decoder]: Decoder[T] = new Decoder[T] {
    override def apply(c: HCursor): Decoder.Result[T] = {
      val s = c.value.noSpacesSortKeys
      val t = XContentCodec.decodeUnsafeFromString[T](s)
      Right(t)
    }
  }

  def encode[T: Encoder](t: T): Json = implicitly[Encoder[T]].apply(t)
  def nospaces[T: Encoder](t: T): String = encode(t).noSpaces
  def decode[T: Decoder](c: HCursor): Either[DecodingFailure, T] = implicitly[Decoder[T]].apply(c)
  def decodeJson[T: Decoder](j: Json): Either[DecodingFailure, T] = implicitly[Decoder[T]].decodeJson(j)
  def parse(s: String): Either[Error, Json] = io.circe.parser.parse(s)
  def decodeGet[T: Decoder](c: HCursor): T = decode[T](c).toTry.get
  def decodeJsonGet[T: Decoder](j: Json): T = decodeJson[T](j).toTry.get
  def parseGet(s: String): Json = parse(s).toTry.get

}
