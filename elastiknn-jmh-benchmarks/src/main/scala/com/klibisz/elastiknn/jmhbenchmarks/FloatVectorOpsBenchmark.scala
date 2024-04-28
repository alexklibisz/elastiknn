package com.klibisz.elastiknn.jmhbenchmarks

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.vectors._
import org.apache.lucene.util.VectorUtil
import org.openjdk.jmh.annotations._

import scala.util.Random

@State(Scope.Benchmark)
class FloatVectorOpsBenchmarkState {
  private given rng: Random = new Random(0)
  val v1: Array[Float] = Vec.DenseFloat.random(999).values
  val v2: Array[Float] = Vec.DenseFloat.random(999).values
  val panama = new PanamaFloatVectorOps
  val default = new DefaultFloatVectorOps
}

class FloatVectorOpsBenchmark {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 1, iterations = 5)
  @Measurement(time = 1, iterations = 5)
  def cosineSimilarityPanama(state: FloatVectorOpsBenchmarkState): Double =
    state.panama.cosineSimilarity(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 1, iterations = 5)
  @Measurement(time = 1, iterations = 5)
  def cosineSimilarityDefault(state: FloatVectorOpsBenchmarkState): Double =
    state.default.cosineSimilarity(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 1, iterations = 5)
  @Measurement(time = 1, iterations = 5)
  def dotProductDefault(state: FloatVectorOpsBenchmarkState): Double =
    state.default.dotProduct(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 1, iterations = 5)
  @Measurement(time = 1, iterations = 5)
  def dotProductPanama(state: FloatVectorOpsBenchmarkState): Double =
    state.panama.dotProduct(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 1, iterations = 5)
  @Measurement(time = 1, iterations = 5)
  def dotProductPanamaSimple(state: FloatVectorOpsBenchmarkState): Double =
    state.panama.dotProductSimple(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 1, iterations = 5)
  @Measurement(time = 1, iterations = 5)
  def dotProductLucene(state: FloatVectorOpsBenchmarkState): Float =
    VectorUtil.dotProduct(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 1, iterations = 5)
  @Measurement(time = 1, iterations = 5)
  def l1DistancePanama(state: FloatVectorOpsBenchmarkState): Double =
    state.panama.l1Distance(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 1, iterations = 5)
  @Measurement(time = 1, iterations = 5)
  def l1DistanceDefault(state: FloatVectorOpsBenchmarkState): Double =
    state.default.l1Distance(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 1, iterations = 5)
  @Measurement(time = 1, iterations = 5)
  def l2DistancePanama(state: FloatVectorOpsBenchmarkState): Double =
    state.panama.l2Distance(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 1, iterations = 5)
  @Measurement(time = 1, iterations = 5)
  def l2DistancePanamaSimple(state: FloatVectorOpsBenchmarkState): Double =
    state.panama.l2DistanceSimple(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 1, iterations = 5)
  @Measurement(time = 1, iterations = 5)
  def l2DistanceDefault(state: FloatVectorOpsBenchmarkState): Double =
    state.default.l2Distance(state.v1, state.v2)

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 1, iterations = 5)
  @Measurement(time = 1, iterations = 5)
  def l2DistanceLucene(state: FloatVectorOpsBenchmarkState): Double =
    Math.sqrt(VectorUtil.squareDistance(state.v1, state.v2) * 1d)
}
