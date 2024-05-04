package com.klibisz.elastiknn

import org.scalatest.matchers.should.Matchers

trait SilentMatchers {

  // See: https://stackoverflow.com/questions/26200768/scalatest-how-to-use-forevery-without-printing-the-entire-collection

  // To use this trait you must extend matchers.
  this: Matchers =>

  private class SilentSeqInner[E](list: IterableOnce[E]) extends Seq[E] {
    var silent: Boolean = false

    def length: Int = 0

    def apply(i: Int): E = list.iterator.next()

    def iterator: Iterator[E] = {
      if (silent)
        Seq.empty.iterator
      else {
        silent = true
        list.iterator
      }
    }
  }

  extension [E](list: IterableOnce[E]) {
    def silent: Seq[E] = new SilentSeqInner(list)
  }
}
