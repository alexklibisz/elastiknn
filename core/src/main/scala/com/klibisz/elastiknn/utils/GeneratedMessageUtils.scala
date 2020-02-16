package com.klibisz.elastiknn.utils

import com.google.common.io.BaseEncoding
import io.circe.{Decoder, DecodingFailure, HCursor}
import scalapb.descriptors._
import scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}
import scalapb_circe.JsonFormat

import scala.collection.JavaConverters._
import scala.util.Try

trait GeneratedMessageUtils {

  // Also tried using java.util.Base64 but found it caused a lot more GC.
  private lazy val b64 = BaseEncoding.base64

  implicit class GeneratedMessageImplicits(gm: GeneratedMessage) {

    /** Convert to a weakly typed map. Convenient for Elasticsearch APIs which use java.util.Map[String,Object]. */
    def asJavaMap: java.util.Map[String, Any] = {

      def convert(pv: PValue): Any = pv match {
        case PEmpty         => null
        case PInt(i)        => i
        case PLong(l)       => l
        case PString(s)     => s
        case PDouble(d)     => d
        case PFloat(f)      => f
        case PByteString(b) => b
        case PBoolean(b)    => b
        case PEnum(e)       => e.index
        case PRepeated(r)   => r.map(convert).asJava
        case PMessage(m)    => convertMap(m)
      }

      def convertMap(m: Map[FieldDescriptor, PValue]): java.util.Map[String, Any] =
        m.map {
          case (fd: FieldDescriptor, pv: PValue) => fd.scalaName -> convert(pv)
        }.asJava

      convertMap(gm.toPMessage.value)
    }

    def toBase64: String = b64.encode(gm.toByteArray)

  }

  implicit class GeneratedCompanionImplicits[M <: GeneratedMessage with Message[M]](cmp: GeneratedMessageCompanion[M]) {
    def parseBase64(s: String): M = cmp.parseFrom(b64.decode(s))
  }

  implicit def decoder[M <: GeneratedMessage with Message[M]: GeneratedMessageCompanion]: Decoder[M] =
    (c: HCursor) => Try(JsonFormat.fromJson(c.value)).toEither.left.map(ex => DecodingFailure(ex.getMessage, Nil))

}

object GeneratedMessageUtils extends GeneratedMessageUtils
