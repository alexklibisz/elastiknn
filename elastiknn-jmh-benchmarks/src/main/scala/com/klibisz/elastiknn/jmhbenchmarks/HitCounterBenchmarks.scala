package com.klibisz.elastiknn.jmhbenchmarks

import org.openjdk.jmh.annotations._
import org.apache.lucene.util.hppc.IntIntHashMap
import org.eclipse.collections.impl.map.mutable.primitive.IntShortHashMap

import scala.util.Random

@State(Scope.Benchmark)
class HitCounterBenchmarksFixtures {
  val rng = new Random(0)
  val numDocs = 60000
  val numHits = 2000
  val initialMapSize = 1000
  val docs = (1 to numHits).map(_ => rng.nextInt(numDocs)).toArray
  val arr = new Array[Int](numDocs)
}

class HitCounterBenchmarks {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 5)
  @Measurement(time = 5, iterations = 5)
  def arrayCountBaseline(f: HitCounterBenchmarksFixtures): Unit = {
    val arr = new Array[Int](f.numDocs)
    for (d <- f.docs) arr.update(d, arr(d) + 1)
    ()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 5)
  @Measurement(time = 5, iterations = 5)
  def hashMapGetOrDefault(f: HitCounterBenchmarksFixtures): Unit = {
    val h = new java.util.HashMap[Int, Int](f.initialMapSize, 0.99f)
    for (d <- f.docs) h.put(d, h.getOrDefault(d, 0) + 1)
    ()
  }


  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 5)
  @Measurement(time = 5, iterations = 5)
  def luceneIntIntHashMap(f: HitCounterBenchmarksFixtures): Unit = {
    val m = new IntIntHashMap(f.initialMapSize, 0.99d)
    for (d <- f.docs) m.putOrAdd(d, 1, 1)
    ()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 5)
  @Measurement(time = 5, iterations = 5)
  def eclipseIntShortHashMapAddToValue(f: HitCounterBenchmarksFixtures): Unit = {
    val m = new IntShortHashMap(f.initialMapSize)
    for (d <- f.docs) m.addToValue(d, 1)
    ()
  }
}
