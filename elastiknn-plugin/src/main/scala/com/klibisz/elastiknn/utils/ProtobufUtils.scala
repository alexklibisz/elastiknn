package com.klibisz.elastiknn.utils

import scalapb.GeneratedMessage
import scalapb.descriptors._

object ProtobufUtils {

  implicit class RichGeneratedMessage(gm: GeneratedMessage) {

    def asMap: Map[String, Any] = {

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
        case PRepeated(r)   => r.map(convert)
        case PMessage(m)    => convertMap(m)
      }

      def convertMap(m: Map[FieldDescriptor, PValue]): Map[String, Any] = m.map {
        case (fd: FieldDescriptor, pv: PValue) => fd.scalaName -> convert(pv)
      }

      convertMap(gm.toPMessage.value)
    }

  }

}
