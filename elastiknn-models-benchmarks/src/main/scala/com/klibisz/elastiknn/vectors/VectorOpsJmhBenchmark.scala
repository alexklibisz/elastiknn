package com.klibisz.elastiknn.vectors

import com.klibisz.elastiknn.api.Vec
import org.openjdk.jmh.annotations._

import scala.util.Random

@State(Scope.Benchmark)
class BenchmarkState {
  implicit private val rng = new Random(0)
  val v1 = Vec.DenseFloat.random(999).values
  val v2 = Vec.DenseFloat.random(999).values
  val pfvo = new PanamaFloatVectorOps
  val dfvo = new DefaultFloatVectorOps
}

class VectorOpsJmhBenchmark {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 3)
  @Measurement(time = 5, iterations = 3)
  def cosineSimilarityPanama(state: BenchmarkState): Unit =
    state.pfvo.cosineSimilarity(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 3)
  @Measurement(time = 5, iterations = 3)
  def cosineSimilarityDefault(state: BenchmarkState): Double =
    state.dfvo.cosineSimilarity(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 3)
  @Measurement(time = 5, iterations = 3)
  def dotProductPanama(state: BenchmarkState): Unit =
    state.pfvo.dotProduct(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 3)
  @Measurement(time = 5, iterations = 3)
  def dotProductDefault(state: BenchmarkState): Double =
    state.dfvo.cosineSimilarity(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 3)
  @Measurement(time = 5, iterations = 3)
  def l1DistancePanama(state: BenchmarkState): Unit =
    state.pfvo.l1Distance(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 3)
  @Measurement(time = 5, iterations = 3)
  def l1DistanceDefault(state: BenchmarkState): Double =
    state.dfvo.l1Distance(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 3)
  @Measurement(time = 5, iterations = 3)
  def l2DistancePanama(state: BenchmarkState): Unit =
    state.pfvo.l2Distance(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 3)
  @Measurement(time = 5, iterations = 3)
  def l2DistanceDefault(state: BenchmarkState): Double =
    state.dfvo.l2Distance(state.v1, state.v2)
}
