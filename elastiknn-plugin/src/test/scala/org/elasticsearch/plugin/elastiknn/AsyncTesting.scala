package org.elasticsearch.plugin.elastiknn

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

trait AsyncTesting {

  def defaultAwaitDuration: Duration = 10.seconds

  final def await[T](f: => Future[T]): Unit = {
    Await.result(f, defaultAwaitDuration)
    ()
  }

  final def await[T](dur: Duration = defaultAwaitDuration)(
      f: => Future[T]): Unit = {
    Await.result(f, dur)
    ()
  }

}
