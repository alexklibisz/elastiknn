package com.klibisz.elastiknn

import io.circe.syntax._
import io.circe.{Encoder, Json}

import scala.collection.JavaConverters._

object circe {

  implicit def objectEncoder: Encoder[Object] = {
    case s: String  => s.asJson
    case i: Integer => Json.fromInt(i)
    case m: java.util.Map[_, _]
        if m.keySet.asScala.forall(_.isInstanceOf[String]) =>
      m.asInstanceOf[java.util.Map[String, Object]].asScala.asJson
  }

  implicit def mapEncoder: Encoder[java.util.Map[String, Object]] = {
    case h: java.util.Map[String, Object] => h.asScala.asJson
  }

}
