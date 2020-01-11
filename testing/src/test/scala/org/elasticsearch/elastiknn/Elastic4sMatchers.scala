package org.elasticsearch.elastiknn

import com.sksamuel.elastic4s.Response
import org.scalatest.{Assertion, Matchers}

trait Elastic4sMatchers {

  this: Matchers =>

  implicit class ResponseMatchers[+U](r: Response[U]) {
    def shouldBeSuccess: Assertion = withClue(r.body.getOrElse(r.toString)) {
      r.isSuccess shouldBe true
    }
  }

}
