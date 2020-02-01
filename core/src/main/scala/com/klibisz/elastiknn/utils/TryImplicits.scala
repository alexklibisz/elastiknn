package com.klibisz.elastiknn.utils

import scala.collection.generic.CanBuildFrom
import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

trait TryImplicits {

  implicit class TraversableOnceOfTry[A, M[X] <: TraversableOnce[X]](trv: M[Try[A]]) {

    /**
      * Convert a `TraversableOnce[Try[Foo]]` into a `Try[TraversableOnce[Foo]]`.
      * Similar to [[scala.concurrent.Future.sequence]], it will fail on the first `Failure` and only return
      * a `Success` if all members are `Success[A]`.
      * @return a Try of the `TraversableOnce` of the results.
      */
    def sequence(implicit cbf: CanBuildFrom[M[Try[A]], A, M[A]]): Try[Vector[A]] = {
      val z: Try[Vector[A]] = Success(Vector.empty[A])
      trv.foldLeft(z) {
        case (Success(acc), Success(t)) => Success(acc :+ t)
        case (_, Failure(ex))           => Failure(ex)
        case (acc @ Failure(_), _)      => acc
      }
    }
  }

}
