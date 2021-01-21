package com.klibisz.elastiknn.utils

import java.{lang, util}

import com.klibisz.elastiknn.ElastiknnException.ElastiknnRuntimeException
import io.circe.{Encoder, Json, JsonObject}

import scala.collection.JavaConverters._

trait CirceUtils {

  def encodeAny(a: Any): Json = a match {
    case s: lang.String  => Json.fromString(s)
    case l: lang.Long    => Json.fromLong(l)
    case i: lang.Integer => Json.fromInt(i)
    case d: lang.Double  => Json.fromDoubleOrNull(d)
    case f: lang.Float   => Json.fromFloatOrNull(f)
    case b: lang.Boolean => Json.fromBoolean(b)
    case l: util.List[_] => Json.fromValues(l.asScala.map(encodeAny))
    case m: util.Map[_, _] =>
      val iterable = m.asScala.map(x => x._1.toString -> encodeAny(x._2))
      Json.fromJsonObject(JsonObject.fromIterable(iterable))
    case null  => null
    case other => throw new RuntimeException(s"Couldn't encode object $other to Json")
  }

  implicit def javaMapEncoder: Encoder[util.Map[String, Object]] = new Encoder[util.Map[String, Object]] {
    private def encodeAny(a: Any): Json = a match {
      case s: lang.String  => Json.fromString(s)
      case l: lang.Long    => Json.fromLong(l)
      case i: lang.Integer => Json.fromInt(i)
      case d: lang.Double  => Json.fromDoubleOrNull(d)
      case f: lang.Float   => Json.fromFloatOrNull(f)
      case b: lang.Boolean => Json.fromBoolean(b)
      case l: util.List[_] => Json.fromValues(l.asScala.map(encodeAny))
      case m: util.Map[_, _] =>
        val iterable = m.asScala.map(x => x._1.toString -> encodeAny(x._2))
        Json.fromJsonObject(JsonObject.fromIterable(iterable))
      case null  => null
      case other => throw new ElastiknnRuntimeException(s"Couldn't encode object $other to Json")
    }
    override def apply(a: util.Map[String, Object]): Json = encodeAny(a)
  }

  implicit def javaListEncoder: Encoder[util.List[Object]] = new Encoder[util.List[Object]] {
    override def apply(l: util.List[Object]): Json = Json.fromValues(l.asScala.map(encodeAny))
  }

}

object CirceUtils extends CirceUtils
