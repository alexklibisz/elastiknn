package com.klibisz.elastiknn.search

import org.apache.lucene.search.DocIdSetIterator
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

final class ArrayHitCounterSpec extends AnyFreeSpec with Matchers {

  final class ReferenceDocIdSetIterator(docIds: Array[Int]) extends DocIdSetIterator {

    private var currentDocIdIndex = -1;
    override def docID(): Int = if (currentDocIdIndex < docIds.length) docIds(currentDocIdIndex) else DocIdSetIterator.NO_MORE_DOCS
    override def nextDoc(): Int = {
      currentDocIdIndex += 1
      docID()
    }
    override def advance(target: Int): Int = {
      while (docID() < target) nextDoc()
      docID()
    }
    override def cost(): Long = docIds.length
  }

  final class Reference(referenceCapacity: Int) extends HitCounter {
    private val counts = scala.collection.mutable.Map[Int, Short]().withDefaultValue(0)

    override def increment(key: Int): Unit = counts.update(key, (counts(key) + 1).toShort)

    override def increment(key: Int, count: Short): Unit = counts.update(key, (counts(key) + count).toShort)

    override def get(key: Int): Short = counts(key)

    override def capacity(): Int = this.referenceCapacity

    override def docIdSetIterator(k: Int): DocIdSetIterator = {
      // A very naive/inefficient way to implement the DocIdSetIterator.
      val valuesSorted = counts.values.toArray.sorted.reverse
      val kthGreatest = valuesSorted.take(k).last
      val greaterDocIds = counts.filter(_._2 > kthGreatest).keys.toArray
      val equalDocIds = counts.filter(_._2 == kthGreatest).keys.toArray.sorted.take(k - greaterDocIds.length)
      val selectedDocIds = (equalDocIds ++ greaterDocIds).sorted
      println(counts.toList.sorted)

      new ReferenceDocIdSetIterator(selectedDocIds)
    }
  }

  private def consumeDocIdSetIterator(disi: DocIdSetIterator): List[Int] = {
    val docIds = new ArrayBuffer[Int]
    while (disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
      docIds.append(disi.docID())
    }
    docIds.toList
  }

  "reference examples" - {
    "example 1" in {
      val c = new Reference(10)
      c.capacity() shouldBe 10

      c.get(0) shouldBe 0
      c.increment(0)
      c.get(0) shouldBe 1

      c.get(1) shouldBe 0
      c.increment(1, 5)
      c.get(1) shouldBe 5

      c.get(2) shouldBe 0
      c.increment(2)
      c.get(2) shouldBe 1
      c.increment(2)
      c.get(2) shouldBe 2

      // The k=2 most frequent doc IDs are 1 and 2.
      val docIds = consumeDocIdSetIterator(c.docIdSetIterator(2))
      docIds shouldBe List(1, 2)
    }
  }

  "randomized comparison to reference" in {
    val seed =  0L // System.currentTimeMillis()
    val rng = new Random(seed)
    val numDocs = 10
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
      val k = rng.nextInt(numDocs)
      val actualDocIds = consumeDocIdSetIterator(ahc.docIdSetIterator(k))
      val referenceDocIds = consumeDocIdSetIterator(ref.docIdSetIterator(k))

      println(k)
      println((actualDocIds.length, actualDocIds))
      println((referenceDocIds.length, referenceDocIds))
      println("---")

      referenceDocIds shouldBe actualDocIds
    }
  }
}
