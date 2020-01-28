package org.elasticsearch.elastiknn.utils

import java.lang
import java.util

import io.circe.syntax._
import io.circe.{Encoder, Json}

import scala.collection.JavaConverters._

object CirceUtils {

  // This is a hack around not being able to figure out XContentParser and the related garbage for Json parsing.
  implicit def objectEncoder: Encoder[Object] = {
    def encode(obj: Any): Json = obj match {
      case s: lang.String  => s.asJson
      case l: lang.Long    => l.asJson
      case i: lang.Integer => i.asJson
      case d: lang.Double  => d.asJson
      case f: lang.Float   => f.asJson
      case b: lang.Boolean => b.asJson
      case l: util.List[_] => Json.fromValues(l.asScala.map(encode))
      case m: util.Map[_, _] if m.keySet.asScala.forall(_.isInstanceOf[String]) =>
        m.asScala.map {
          case (k, v) => k.asInstanceOf[String] -> encode(v)
        }.asJson
      case null => null
    }
    encode _
  }

  implicit def javaMapEncoder: Encoder[util.Map[lang.String, Object]] = {
    case m: util.Map[String, Object] => m.asScala.asJson
  }

  implicit def scalaMapEncoder: Encoder[Map[String, AnyRef]] = {
    case m: Map[String, AnyRef] => m.asInstanceOf[Map[String, Object]].asJson
  }

}
