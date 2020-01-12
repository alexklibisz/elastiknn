package org.elasticsearch.elastiknn

import scala.annotation.tailrec

package object utils {

  @tailrec
  private[elastiknn] def fastfor(i: Int, pred: Int => Boolean, inc: Int => Int)(f: Int => Unit): Unit =
    if (pred(i)) {
      f(i)
      fastfor(inc(i), pred, inc)(f)
    } else ()

  private[elastiknn] def fastfor(i: Int, pred: Int => Boolean)(f: Int => Unit): Unit = fastfor(i, pred, _ + 1)(f)

}
