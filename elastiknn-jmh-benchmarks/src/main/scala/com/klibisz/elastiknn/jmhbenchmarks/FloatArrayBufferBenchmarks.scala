package com.klibisz.elastiknn.jmhbenchmarks

import com.klibisz.elastiknn.api.FloatArrayBuffer
import org.openjdk.jmh.annotations.*

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

@State(Scope.Benchmark)
class FloatArrayBufferBenchmarksState {
  implicit private val rng: Random = new Random(0)
  val lst768 = (0 until 768).map(_ => rng.nextFloat()).toList
}

class FloatArrayBufferBenchmarks {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def scalaAppend42to768(state: FloatArrayBufferBenchmarksState): Unit = {
    val buf = new ArrayBuffer[Float](42)
    state.lst768.foreach(buf.append)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def scalaAppend768to768(state: FloatArrayBufferBenchmarksState): Unit = {
    val buf = new ArrayBuffer[Float](768)
    state.lst768.foreach(buf.append)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def customAppend42to768(state: FloatArrayBufferBenchmarksState): Unit = {
    val buf = new FloatArrayBuffer(42)
    state.lst768.foreach(buf.append)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def customAppend768to768(state: FloatArrayBufferBenchmarksState): Unit = {
    val buf = new FloatArrayBuffer(768)
    state.lst768.foreach(buf.append)
  }
}
