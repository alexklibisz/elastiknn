package com.klibisz.elastiknn.jmhbenchmarks

import com.klibisz.elastiknn.search.QuickSelect
import org.apache.lucene.search.KthGreatest
import org.openjdk.jmh.annotations._

import scala.util.Random

@State(Scope.Benchmark)
class KthGreatestBenchmarkFixtures {
  val rng = new Random(0)
  val k = 1000
  val numDocs = 60000
  val intCounts: Array[Int] = (0 until numDocs).map(_ => rng.nextInt(Short.MaxValue)).toArray
  val shortCounts: Array[Short] = intCounts.map(_.toShort)
}

class KthGreatestBenchmarks {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 5)
  @Measurement(time = 5, iterations = 5)
  def sortBaseline(f: KthGreatestBenchmarkFixtures): Unit = {
    val sorted = f.intCounts.sorted
    val _ = sorted.apply(f.intCounts.length - f.k)
    ()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 5)
  @Measurement(time = 5, iterations = 5)
  def kthGreatest(f: KthGreatestBenchmarkFixtures): Unit = {
    KthGreatest.kthGreatest(f.shortCounts, f.k)
    ()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 5)
  @Measurement(time = 5, iterations = 5)
  def unnikedRecursive(f: KthGreatestBenchmarkFixtures): Unit = {
    QuickSelect.selectRecursive(f.intCounts, f.k)
    ()
  }
}
