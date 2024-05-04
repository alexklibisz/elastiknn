package com.klibisz.elastiknn

import com.sksamuel.elastic4s.Response
import org.scalatest.matchers.should.Matchers
import org.scalatest.Assertion

trait Elastic4sMatchers {

  this: Matchers =>

  extension [U](r: Response[U]) {
    def shouldBeSuccess: Assertion = withClue(r.body.getOrElse(r.toString)) {
      r.isSuccess shouldBe true
    }
  }
}
