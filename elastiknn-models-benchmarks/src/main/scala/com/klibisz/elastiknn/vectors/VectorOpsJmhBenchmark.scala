package com.klibisz.elastiknn.vectors

import com.klibisz.elastiknn.api.Vec
import org.openjdk.jmh.annotations._

import scala.util.Random

@State(Scope.Benchmark)
class BenchmarkState {
  implicit private val rng: Random = new Random(0)
  val v1 = Vec.DenseFloat.random(999).values
  val v2 = Vec.DenseFloat.random(999).values
  val panama = new PanamaFloatVectorOps
  val default = new DefaultFloatVectorOps
}

class VectorOpsJmhBenchmark {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 6)
  @Measurement(time = 5, iterations = 6)
  def cosineSimilarityPanama(state: BenchmarkState): Double =
    state.panama.cosineSimilarity(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 6)
  @Measurement(time = 5, iterations = 6)
  def cosineSimilarityDefault(state: BenchmarkState): Double =
    state.default.cosineSimilarity(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 6)
  @Measurement(time = 5, iterations = 6)
  def dotProductDefault(state: BenchmarkState): Double =
    state.default.dotProduct(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 6)
  @Measurement(time = 5, iterations = 6)
  def dotProductPanama(state: BenchmarkState): Double =
    state.panama.dotProduct(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 6)
  @Measurement(time = 5, iterations = 6)
  def l1DistancePanama(state: BenchmarkState): Double =
    state.panama.l1Distance(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 6)
  @Measurement(time = 5, iterations = 6)
  def l1DistanceDefault(state: BenchmarkState): Double =
    state.default.l1Distance(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 6)
  @Measurement(time = 5, iterations = 6)
  def l2DistancePanama(state: BenchmarkState): Double =
    state.panama.l2Distance(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 6)
  @Measurement(time = 5, iterations = 6)
  def l2DistanceDefault(state: BenchmarkState): Double =
    state.default.l2Distance(state.v1, state.v2)
}
