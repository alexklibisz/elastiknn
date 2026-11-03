package com.klibisz.elastiknn.jmhbenchmarks

import com.klibisz.elastiknn.search.ArrayHitCounter
import org.openjdk.jmh.annotations.*
import org.apache.lucene.search.DocIdSetIterator

import scala.util.Random

@State(Scope.Benchmark)
class HitCounterBenchmarksFixtures {
  val rng = new Random(0)
  val numDocs = 60000
  val numHits = 30000
  val candidates = 1000
  val docs: Array[Int] = (1 to numHits).map(_ => rng.nextInt(numDocs)).toArray
  val maxCount = docs.groupBy(identity).keys.max
}

class HitCounterBenchmarks {

  private def consumeDocIdSetIterator(disi: DocIdSetIterator): Unit = {
    while (disi.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
      val _ = disi.docID()
    }
    ()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 5)
  @Measurement(time = 5, iterations = 10)
  def arrayHitCounter(f: HitCounterBenchmarksFixtures): Unit = {
    val ahc = new ArrayHitCounter(f.numDocs, f.maxCount)
    for (d <- f.docs) ahc.increment(d)
    consumeDocIdSetIterator(ahc.docIdSetIterator(f.candidates))
    ()
  }
}
