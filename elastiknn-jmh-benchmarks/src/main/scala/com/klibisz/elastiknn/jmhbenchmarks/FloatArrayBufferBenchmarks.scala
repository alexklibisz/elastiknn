package com.klibisz.elastiknn.jmhbenchmarks

import com.klibisz.elastiknn.api.FloatArrayBuffer
import org.openjdk.jmh.annotations._

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

@State(Scope.Benchmark)
class FloatArrayBufferBenchmarksState {
  private given rng: Random = new Random(0)
  val lst768 = (0 until 768).map(_ => rng.nextFloat()).toList
}

class FloatArrayBufferBenchmarks {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def scalaAppendFixedInitialSize(state: FloatArrayBufferBenchmarksState): Int = {
    val buf = new ArrayBuffer[Float]()
    state.lst768.foreach(buf.append)
    buf.toArray.length
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def scalaAppendKnownInitialSize(state: FloatArrayBufferBenchmarksState): Int = {
    val buf = new ArrayBuffer[Float](768)
    state.lst768.foreach(buf.append)
    buf.toArray.length
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @Fork(value = 1)
  @Warmup(time = 5, iterations = 1)
  @Measurement(time = 5, iterations = 1)
  def customAppend(state: FloatArrayBufferBenchmarksState): Int = {
    val buf = new FloatArrayBuffer()
    state.lst768.foreach(buf.append)
    buf.toArray.length
  }
}
