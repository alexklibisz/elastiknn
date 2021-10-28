package com.klibisz.elastiknn.utils

import java.util

import io.circe.{Json, JsonObject}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._

class CirceUtilsSuite extends AnyFunSuite with Matchers with CirceUtils {

  val map = new java.util.HashMap[String, Object] {
    put("foo", Integer.valueOf(1))
    put(
      "bar",
      new util.HashMap[String, Object] {
        put("nums", List(1, 2, 3).asJava)
        put("strings", List("one", "two", "three").asJava)
        put("anys", List("string", 42).asJava)
      }
    )
  }

  test("encode scala map to json") {
    val encoded = javaMapEncoder(map)
    encoded shouldBe Json.fromJsonObject(
      JsonObject(
        "foo" -> Json.fromInt(1),
        "bar" -> Json.fromJsonObject(JsonObject(
          "nums" -> Json.fromValues(
            Seq(
              Json.fromInt(1),
              Json.fromInt(2),
              Json.fromInt(3)
            )),
          "strings" -> Json.fromValues(
            Seq(
              Json.fromString("one"),
              Json.fromString("two"),
              Json.fromString("three")
            )),
          "anys" -> Json.fromValues(Seq(
            Json.fromString("string"),
            Json.fromInt(42)
          ))
        ))
      ))

  }

}
