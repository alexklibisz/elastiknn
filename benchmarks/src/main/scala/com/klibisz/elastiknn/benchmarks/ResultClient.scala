package com.klibisz.elastiknn.benchmarks

import zio.IO

object ResultClient {

  trait Service {
    def allResults: IO[Throwable, Set[Result]]
    def findResult: IO[Throwable, Option[Result]]
    def saveResult: IO[Throwable, Unit]
  }

}
