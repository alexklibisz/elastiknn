package com.klibisz.elastiknn.utils

import com.google.common.io.BaseEncoding
import scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}
import scalapb.descriptors._

import scala.collection.JavaConverters._

object ProtobufUtils {

  implicit class RichGeneratedMessage(gm: GeneratedMessage) {

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

    def toBase64: String = BaseEncoding.base64.encode(gm.toByteArray)

  }

  implicit class RichGeneratedCompanion[M <: GeneratedMessage with Message[M]](cmp: GeneratedMessageCompanion[M]) {
    def parseBase64(s: String): M = cmp.parseFrom(BaseEncoding.base64.decode(s))
  }

}
