package com.klibisz.elastiknn.utils

import org.scalatest.{FunSuite, Inspectors, Matchers}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Random

class FutureUtilsSuite extends FunSuite with Matchers with Inspectors with FutureUtils {

  implicit val ec: ExecutionContext = ExecutionContext.global

  test("pipeline handles potential race conditions in fizzbuzz") {

    def fizzbuzz(i: Int): String =
      if (i % 3 == 0 && i % 5 == 0) "fizbuzz"
      else if (i % 3 == 0) "fizz"
      else if (i % 5 == 0) "buzz"
      else ""
    val inputs = (1 to 500).toVector
    val correct = inputs.map(fizzbuzz)

    val rng = new Random(0)
    val sleepTimes = inputs.map(_ => rng.nextInt(290) + 10)
    val pipelined = pipeline(inputs.zip(sleepTimes), 10) {
      case (i, s) => Future(Thread.sleep(s)).map(_ => fizzbuzz(i))
    }

    val res = Await.result(pipelined, Duration("10 seconds"))
    res shouldBe correct

  }

}
