package com.klibisz.elastiknn.microbenchmarks

import org.openjdk.jmh.annotations._
import org.apache.lucene.util.hppc.IntIntHashMap

import scala.util.Random

@State(Scope.Benchmark)
class HitCounterBenchmarksFixtures {
  val rng = new Random(0)
  val numDocs = 1000000
  val numHits = 10000
  val candidates = 1000
  val docs = (1 to numHits).map(_ => rng.nextInt(numDocs)).toArray
}

class HitCounterBenchmarks {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def arrayCountBaseline(f: HitCounterBenchmarksFixtures): Unit = {
    val arr = new Array[Int](f.numDocs)
    for (d <- f.docs) arr.update(d, arr(d) + 1)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def hashMapGetOrDefault(f: HitCounterBenchmarksFixtures): Unit = {
    val h = new java.util.HashMap[Int, Int](f.numDocs, 0.99f)
    for (d <- f.docs) h.put(d, h.getOrDefault(d, 0) + 1)
  }


  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def luceneIntIntHashMap(f: HitCounterBenchmarksFixtures): Unit = {
    val m = new IntIntHashMap(f.numDocs, 0.99d)
    for (d <- f.docs) m.putOrAdd(d, 1, 1)
  }
}
