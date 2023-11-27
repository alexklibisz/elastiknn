package com.klibisz.elastiknn.jmhbenchmarks

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.vectors._
import org.openjdk.jmh.annotations._

import scala.util.Random

@State(Scope.Benchmark)
class CachedL2DistanceBenchmarkState {
  implicit private val rng: Random = new Random(0)
  val dims = 768
  val v1 = Vec.DenseFloat.random(dims)
  val v2 = Vec.DenseFloat.random(dims)
  val panama = new PanamaFloatVectorOps
  val cached = new CachedL2Distance(v1.values)

  // Sanity check.
  val expected = panama.l2Distance(v1.values, v2.values)
  val actual = cached.l2Distance(v2.values)
  require(actual == expected, (actual, expected))
}

class CachedL2DistanceBenchmark {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 6)
  @Measurement(time = 5, iterations = 6)
  def baseline(state: CachedL2DistanceBenchmarkState): Double =
    state.panama.l2Distance(state.v1.values, state.v2.values)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 6)
  @Measurement(time = 5, iterations = 6)
  def cached(state: CachedL2DistanceBenchmarkState): Double = {
    state.cached.l2Distance(state.v2.values)

  }

}


