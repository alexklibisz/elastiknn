package com.klibisz.elastiknn

import scala.concurrent.{ExecutionContext, Future}

object FutureUtils {

  def traverseSerially[A, B](in: IterableOnce[A])(f: A => Future[B])(using ec: ExecutionContext): Future[Seq[B]] =
    in.iterator.foldLeft(Future.successful(Vector.empty[B])) { case (accFuture, next) =>
      for {
        acc <- accFuture
        result <- f(next)
      } yield acc :+ result
    }

}
