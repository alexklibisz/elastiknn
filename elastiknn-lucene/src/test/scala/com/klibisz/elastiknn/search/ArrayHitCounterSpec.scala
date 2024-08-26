package com.klibisz.elastiknn.search

import org.apache.lucene.search.DocIdSetIterator
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

final class ArrayHitCounterSpec extends AnyFreeSpec with Matchers {

  final class Reference(referenceCapacity: Int) extends HitCounter {
    private val counts = scala.collection.mutable.Map[Int, Short](
      (0 until referenceCapacity).map(_ -> 0.toShort): _*
    )

    override def increment(key: Int): Unit = counts.update(key, (counts(key) + 1).toShort)

    override def increment(key: Int, count: Short): Unit = counts.update(key, (counts(key) + count).toShort)

    override def get(key: Int): Short = counts(key)

    override def capacity(): Int = this.referenceCapacity

    override def docIdSetIterator(k: Int): DocIdSetIterator = DocIdSetIterator.empty()
  }

  "reference examples" - {
    "example 1" in {
      val c = new Reference(10)
      c.capacity() shouldBe 10

      c.get(0) shouldBe 0
      c.increment(0)
      c.get(0) shouldBe 1

      c.get(5) shouldBe 0
      c.increment(5, 5)
      c.get(5) shouldBe 5

      c.get(9) shouldBe 0
      c.increment(9)
      c.get(9) shouldBe 1
      c.increment(9)
      c.get(9) shouldBe 2

//      val kgr = c.kthGreatest(2)
//      kgr.kthGreatest shouldBe 1
//      kgr.numGreaterThan shouldBe 2
//      kgr.numNonZero shouldBe 3
    }
  }

  "randomized comparison to reference" in {
    val seed = System.currentTimeMillis()
    val rng = new Random(seed)
    val numDocs = 60000
    val numMatches = numDocs / 2
    info(s"Using seed $seed")
    for (_ <- 0 until 99) {
      val matches = (0 until numMatches).map(_ => rng.nextInt(numDocs))
      val ref = new Reference(numDocs)
      val ahc = new ArrayHitCounter(numDocs)
      matches.foreach { doc =>
        ref.increment(doc)
        ahc.increment(doc)
        ahc.get(doc) shouldBe ref.get(doc)
        val count = rng.nextInt(10).toShort
        ref.increment(doc, count)
        ahc.increment(doc, count)
        ahc.get(doc) shouldBe ref.get(doc)
      }
//      ahc.minKey() shouldBe ref.minKey()
//      ahc.maxKey() shouldBe ref.maxKey()
//      val k = rng.nextInt(numDocs)
//      val ahcKgr = ahc.kthGreatest(k)
//      val refKgr = ref.kthGreatest(k)
//      ahcKgr shouldBe refKgr
    }
  }
}
