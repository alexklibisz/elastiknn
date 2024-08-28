package com.klibisz.elastiknn.search

import org.apache.lucene.search.DocIdSetIterator
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

final class ArrayHitCounterSpec extends AnyFreeSpec with Matchers {



  private final class ReferenceHitCounter(referenceCapacity: Int) extends HitCounter {

    private final class ArrayDocIdSetIterator(docIds: Array[Int]) extends DocIdSetIterator {

      private var currentDocIdIndex = -1;

      override def docID(): Int = if (currentDocIdIndex < docIds.length) docIds(currentDocIdIndex) else DocIdSetIterator.NO_MORE_DOCS

      override def nextDoc(): Int = {
        currentDocIdIndex += 1
        docID()
      }

      override def advance(target: Int): Int = {
        while (docID() < target) {
          val _ = nextDoc()
        }
        docID()
      }

      override def cost(): Long = docIds.length
    }

    private val counts = scala.collection.mutable.Map[Int, Short]().withDefaultValue(0)

    override def increment(key: Int): Unit = counts.update(key, (counts(key) + 1).toShort)

    override def increment(key: Int, count: Short): Unit = counts.update(key, (counts(key) + count).toShort)

    override def get(key: Int): Short = counts(key)

    override def capacity(): Int = this.referenceCapacity

    override def docIdSetIterator(k: Int): DocIdSetIterator = {
      // A very naive/inefficient way to implement the DocIdSetIterator.
      if (k == 0 || counts.isEmpty) DocIdSetIterator.empty()
      else {
        // This is a hack to replicate a bug in how we emit doc IDs.
        // Basically if the kth greatest value is zero, we end up emitting docs that were never matched,
        // so we need to fill the map with zeros to replicate the behavior here.
        val minKey = counts.keys.min
        val maxKey = counts.keys.max
        (minKey to maxKey).foreach(k => counts.update(k, counts(k)))

        val valuesSorted = counts.values.toArray.sorted.reverse
        val kthGreatest = valuesSorted.take(k).last
        val greaterDocIds = counts.filter(_._2 > kthGreatest).keys.toArray
        val equalDocIds = counts.filter(_._2 == kthGreatest).keys.toArray.sorted.take(k - greaterDocIds.length)
        val selectedDocIds = (equalDocIds ++ greaterDocIds).sorted
        new ArrayDocIdSetIterator(selectedDocIds)
      }
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
      val c = new ReferenceHitCounter(10)
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
    val seed = System.currentTimeMillis()
    val rng = new Random(seed)
    val numDocs = 60000
    val numMatches = numDocs / 2
    info(s"Using seed $seed")
    for (_ <- 0 until 99) {
      val matches = (0 until numMatches).map(_ => rng.nextInt(numDocs))
      val ref = new ReferenceHitCounter(numDocs)
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

      referenceDocIds shouldBe actualDocIds
    }
  }
}
