package com.klibisz.elastiknn.jmhbenchmarks

import com.klibisz.elastiknn.storage.ByteBufferSerialization
import org.openjdk.jmh.annotations._

import scala.util.Random

@State(Scope.Benchmark)
class VectorSerializationBenchmarksState {
  implicit private val rng: Random = new Random(0)
  val floatArray: Array[Float] = (0 until 1000).map(_ => rng.nextFloat()).toArray
  val floatArraySerialized: Array[Byte] = ByteBufferSerialization.writeFloats(floatArray)
  val intArray: Array[Int] = (0 until 1000).map(_ => rng.nextInt()).toArray
  val intArraySerialized: Array[Byte] = ByteBufferSerialization.writeInts(intArray)
  val ints: Array[Int] =
    Array(Int.MinValue + 1, Short.MinValue + 1, Byte.MinValue + 1, 0, Byte.MaxValue - 1, Short.MaxValue - 1, Int.MaxValue - 1)
  val intsSerialized: Array[Array[Byte]] = ints.map(ByteBufferSerialization.writeInt)
}

class VectorSerializationBenchmarks {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def writeFloats_ByteBuffer(state: VectorSerializationBenchmarksState): Array[Byte] = {
    ByteBufferSerialization.writeFloats(state.floatArray)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def readFloats_ByteBuffer(state: VectorSerializationBenchmarksState): Array[Float] = {
    ByteBufferSerialization.readFloats(state.floatArraySerialized, 0, state.floatArraySerialized.length)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def writeInts_ByteBuffer(state: VectorSerializationBenchmarksState): Array[Byte] = {
    ByteBufferSerialization.writeInts(state.intArray)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def readInts_ByteBuffer(state: VectorSerializationBenchmarksState): Array[Int] = {
    ByteBufferSerialization.readInts(state.intArraySerialized, 0, state.intArraySerialized.length)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def writeIntsWithPrefix_ByteBuffer(state: VectorSerializationBenchmarksState): Array[Byte] = {
    ByteBufferSerialization.writeIntsWithPrefix(state.intArray.length, state.intArray)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def writeInt_ByteBuffer(state: VectorSerializationBenchmarksState): Unit = {
    state.ints.foreach(ByteBufferSerialization.writeInt)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def readInt_ByteBuffer(state: VectorSerializationBenchmarksState): Unit = {
    state.intsSerialized.foreach(ByteBufferSerialization.readInt)
  }
}
