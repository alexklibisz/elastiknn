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
  val shortCounts: Array[Short] = (0 until numDocs).map(_ => rng.nextInt(Short.MaxValue).toShort).toArray
  val copy = new Array[Short](shortCounts.length)
}

class KthGreatestBenchmarks {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 5)
  @Measurement(time = 5, iterations = 5)
  def sortBaseline(f: KthGreatestBenchmarkFixtures): Unit = {
    val sorted = f.shortCounts.sorted
    val _ = sorted.apply(f.shortCounts.length - f.k)
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
    System.arraycopy(f.shortCounts, 0, f.copy, 0, f.copy.length)
    QuickSelect.selectRecursive(f.copy, f.k)
    ()
  }
}
