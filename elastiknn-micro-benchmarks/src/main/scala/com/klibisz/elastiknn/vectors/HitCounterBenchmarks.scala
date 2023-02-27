package com.klibisz.elastiknn.vectors

import com.koloboke.collect.map.hash.HashIntShortMaps
import org.agrona.collections.Int2IntCounterMap
import org.eclipse.collections.impl.map.mutable.primitive.{IntIntHashMap, IntShortHashMap}
import org.openjdk.jmh.annotations._
import speiger.src.collections.ints.maps.impl.hash._

import java.util
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
  def speigerInt2IntOpenHashMap(f: HitCounterBenchmarksFixtures): Unit = {
    val m = new Int2IntOpenHashMap(f.candidates * 10)
    for (d <- f.docs) m.addTo(d, 1)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def speigerInt2IntLinkedOpenHashMap(f: HitCounterBenchmarksFixtures): Unit = {
    val m = new Int2IntLinkedOpenHashMap(f.candidates * 10)
    for (d <- f.docs) m.addTo(d, 1)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def speigerInt2ShortOpenHashMap(f: HitCounterBenchmarksFixtures): Unit = {
    val m = new Int2ShortOpenHashMap(f.candidates * 10)
    for (d <- f.docs) m.addTo(d, 1)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def speigerInt2ShortLinkedOpenHashMap(f: HitCounterBenchmarksFixtures): Unit = {
    val m = new Int2ShortLinkedOpenHashMap(f.candidates * 10)
    for (d <- f.docs) m.addTo(d, 1)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def agronaInt2IntCounterMap(f: HitCounterBenchmarksFixtures): Unit = {
    val m = new Int2IntCounterMap(f.candidates * 10)
    for (d <- f.docs) m.incrementAndGet(d)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def kolobokeIntShortMapAddValue(f: HitCounterBenchmarksFixtures): Unit = {
    val m = HashIntShortMaps.newMutableMap(f.candidates * 10)
    for (d <- f.docs) m.addValue(d, 1, 0)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def eclipseIntIntHashMapGetAndPut(f: HitCounterBenchmarksFixtures): Unit = {
    val m = new IntIntHashMap(f.candidates * 10)
    for (d <- f.docs) m.put(d, m.get(d) + 1)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def eclipseIntIntHashMapAddToValue(f: HitCounterBenchmarksFixtures): Unit = {
    val m = new IntIntHashMap(f.candidates * 10)
    for (d <- f.docs) m.addToValue(d, 1)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def eclipseIntShortHashMapGetAndPut(f: HitCounterBenchmarksFixtures): Unit = {
    val m = new IntShortHashMap(f.candidates * 10)
    for (d <- f.docs) m.put(d, (m.get(d) + 1).toShort)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def eclipseIntShortHashMapAddToValue(f: HitCounterBenchmarksFixtures): Unit = {
    val m = new IntShortHashMap(f.candidates * 10)
    for (d <- f.docs) m.addToValue(d, 1)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def hashMapGetOrDefault(f: HitCounterBenchmarksFixtures): Unit = {
    val h = new util.HashMap[Int, Int](f.numDocs)
    for (d <- f.docs) h.put(d, h.getOrDefault(d, 0) + 1)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 1, iterations = 10)
  def arrayCountBaseline(f: HitCounterBenchmarksFixtures): Unit = {
    val arr = new Array[Int](f.numDocs)
    for (d <- f.docs) arr.update(d, arr(d) + 1)
  }
}
