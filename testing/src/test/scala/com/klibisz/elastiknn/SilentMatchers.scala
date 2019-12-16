package com.klibisz.elastiknn

import org.scalatest.Matchers

trait SilentMatchers {

  // See: https://stackoverflow.com/questions/26200768/scalatest-how-to-use-forevery-without-printing-the-entire-collection

  // To use this trait you must extend matchers.
  this: Matchers =>

  implicit class SilentSeq[E](list: TraversableOnce[E]) {

    class SilentSeqInner(list: TraversableOnce[E]) extends Seq[E] {
      var silent: Boolean = false
      def length: Int = 0
      def apply(i: Int): E = list.toIterator.next()
      def iterator: Iterator[E] = {
        if (silent)
          Seq.empty.iterator
        else {
          silent = true
          list.toIterator
        }
      }
    }

    def silent: SilentSeqInner = new SilentSeqInner(list)
  }

}
