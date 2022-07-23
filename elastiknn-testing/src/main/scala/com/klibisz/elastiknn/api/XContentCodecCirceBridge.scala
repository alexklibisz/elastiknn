package com.klibisz.elastiknn.api

import io.circe.{Encoder, Decoder, Json, HCursor}

trait XContentCodecCirceBridge {
  implicit def encoderFromXContent[T: XContentCodec.Encoder]: Encoder[T] = new Encoder[T] {
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
}
