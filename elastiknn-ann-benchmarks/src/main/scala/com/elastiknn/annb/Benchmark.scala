package com.elastiknn.annb

sealed trait Benchmark

object Benchmark {
  case object AnnBenchmarks extends Benchmark
  case object BigAnnBenchmarks extends Benchmark
}
