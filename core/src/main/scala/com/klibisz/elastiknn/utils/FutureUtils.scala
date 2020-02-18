package com.klibisz.elastiknn.utils

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

trait FutureUtils {

  def pipeline[A, B](as: Seq[A], n: Int)(f: A => Future[B])(implicit ec: ExecutionContext): Future[Seq[B]] = {

    object LocalLock
    val promises: Vector[Promise[B]] = as.map(_ => Promise[B]).toVector
    var (init, todo) = as.zipWithIndex.splitAt(n)

    def cb(t: Try[B], i: Int): Unit = {
      promises(i).complete(t)
      if (todo.isEmpty) ()
      else
        LocalLock.synchronized {
          val (a, i) = todo.head
          todo = todo.drop(1)
          f(a).onComplete(b => cb(b, i))
        }
    }

    init.foreach {
      case (a, i) => f(a).onComplete(b => cb(b, i))
    }

    Future.sequence(promises.map(_.future))
  }

}
