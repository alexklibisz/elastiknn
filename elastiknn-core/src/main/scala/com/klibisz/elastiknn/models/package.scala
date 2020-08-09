package com.klibisz.elastiknn

package object models {

  // Large prime number, useful for some of the hashing methods.
  private[models] val HASH_PRIME: Int = 2038074743

  // Looping construct that's faster than using `for` or collection methods and maybe a little slower than `while/var`.
  private[models] def cfor(i: Int)(pred: Int => Boolean, inc: Int => Int)(f: Int => Unit): Unit = {
    var i_ = i
    while (pred(i_)) {
      f(i_)
      i_ = inc(i_)
    }
  }

}
