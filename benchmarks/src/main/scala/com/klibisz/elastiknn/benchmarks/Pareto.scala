package com.klibisz.elastiknn.benchmarks

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.api.Mapping._
import zio._

/**
  * Ingest results and compute pareto curves for each set of results grouped by (dataset, algorithm, k).
  */
object Pareto extends App {

  case class Params(resultsBucket: String,
                    resultsPrefix: String,
                    resultsS3URL: String,
                    paretoBucket: String,
                    paretoPrefix: String,
                    paretoS3URL: String)

  private def mappingToAlgorithm(m: Mapping): String = m match {
    case _: SparseBool                                            => "exact"
    case _: DenseFloat                                            => "exact"
    case _: SparseIndexed                                         => "sparse indexed"
    case _: JaccardLsh | _: HammingLsh | _: AngularLsh | _: L2Lsh => "lsh"
  }

  private def pareto(results: Seq[BenchmarkResult]): Seq[ParetoResult] = {

    def aggRecall(br: BenchmarkResult): Double = br.queryResults.map(_.recall).sum / br.queryResults.length

    def aggThroughput(br: BenchmarkResult): Double = ???

    results
      .groupBy(r => (r.dataset, mappingToAlgorithm(r.mapping), r.k))
      .map {
        case ((dataset, algo, k), results) =>
          results
            .groupBy(aggRecall)
            .mapValues(_.maxBy(aggThroughput))

      }

    ???
  }

  override def run(args: List[String]) = ???
}
