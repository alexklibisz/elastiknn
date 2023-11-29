package com.klibisz.elastiknn.jmhbenchmarks

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.storage.{ByteBufferSerialization, UnsafeSerialization}
import org.openjdk.jmh.annotations._

import scala.util.Random

@State(Scope.Benchmark)
class VectorSerializationBenchmarksState {
  implicit private val rng: Random = new Random(0)
  val floatsOriginal = Vec.DenseFloat.random(999).values
  val floatsSerialized = UnsafeSerialization.writeFloats(floatsOriginal)
}

class VectorSerializationBenchmarks {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 5)
  @Measurement(time = 5, iterations = 5)
  def writeFloatsUnsafe(state: VectorSerializationBenchmarksState): Array[Byte] = {
    UnsafeSerialization.writeFloats(state.floatsOriginal)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 5)
  @Measurement(time = 5, iterations = 5)
  def writeFloatsByteBuffer(state: VectorSerializationBenchmarksState): Array[Byte] = {
    ByteBufferSerialization.writeFloats(state.floatsOriginal)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 5)
  @Measurement(time = 5, iterations = 5)
  def readFloatsUnsafe(state: VectorSerializationBenchmarksState): Array[Float] = {
    UnsafeSerialization.readFloats(state.floatsSerialized, 0, state.floatsSerialized.length)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 5)
  @Measurement(time = 5, iterations = 5)
  def readFloatsByteBuffer(state: VectorSerializationBenchmarksState): Array[Float] = {
    ByteBufferSerialization.readFloats(state.floatsSerialized, 0, state.floatsSerialized.length)
  }

}
