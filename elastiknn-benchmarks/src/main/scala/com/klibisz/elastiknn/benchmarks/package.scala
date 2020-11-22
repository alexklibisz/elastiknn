package com.klibisz.elastiknn

import com.klibisz.elastiknn.api._
import io.circe.Codec
import io.circe.generic.semiauto._
import org.apache.commons.codec.digest.DigestUtils

package object benchmarks {

  sealed abstract class Dataset(val dims: Int) {
    def name: String = this.getClass.getSimpleName.replace("$", "")
  }
  object Dataset {
    case object AmazonHome extends Dataset(4096)
    case object AmazonHomeUnit extends Dataset(4096)
    case object AmazonHomePhash extends Dataset(4096)
    case object AmazonMixed extends Dataset(4096)
    case object AmazonMixedUnit extends Dataset(4096)
    case object AnnbDeep1b extends Dataset(96)
    case object AnnbFashionMnist extends Dataset(784)
    case object AnnbGist extends Dataset(960)
    case object AnnbGlove25 extends Dataset(25)
    case object AnnbGlove100 extends Dataset(100)
    case object AnnbKosarak extends Dataset(27983)
    case object AnnbMnist extends Dataset(784)
    case object AnnbNyt extends Dataset(256)
    case object AnnbSift extends Dataset(128)
  }

  final case class Query(nnq: NearestNeighborsQuery, k: Int)

  final case class Experiment(dataset: Dataset,
                              exactMapping: Mapping,
                              exactQuery: NearestNeighborsQuery,
                              testMapping: Mapping,
                              testQueries: Seq[Query],
                              shards: Int = 1,
                              parallelQueries: Int = 1) {
    def md5sum: String = DigestUtils.md5Hex(codecs.experimentCodec(this).noSpaces).toLowerCase
  }

  final case class QueryResult(scores: Seq[Float], duration: Long, recall: Double = Double.NaN)

  /**
    * This gets serialized and persisted.
    */
  final case class BenchmarkResult(dataset: Dataset,
                                   mapping: Mapping,
                                   query: NearestNeighborsQuery,
                                   k: Int,
                                   shards: Int,
                                   parallelQueries: Int,
                                   durationMillis: Long,
                                   queriesPerSecond: Float,
                                   queryResults: Array[QueryResult]) {
    override def toString: String = s"Result($dataset, $mapping, $query, $k, $shards, $parallelQueries, $durationMillis, ...)"
  }

  /**
    * This gets constructed from a BenchmarkResult and used as a row in a CSV.
    */
  final case class AggregateResult(dataset: String,
                                   similarity: String,
                                   algorithm: String,
                                   k: Int,
                                   mapping: Mapping,
                                   shards: Int,
                                   query: NearestNeighborsQuery,
                                   parallelQueries: Int,
                                   recall: Float,
                                   queriesPerSecond: Float)

  object AggregateResult {

    val header = Seq(
      "dataset",
      "similarity",
      "algorithm",
      "k",
      "mapping",
      "shards",
      "query",
      "parallelQueries",
      "recall",
      "queriesPerSecond"
    )

    private def algorithmName(q: NearestNeighborsQuery): String = {
      import NearestNeighborsQuery._
      q match {
        case _: Exact                                                 => "Exact"
        case _: SparseIndexed                                         => "Sparse Indexed"
        case _: HammingLsh | _: JaccardLsh | _: AngularLsh | _: L2Lsh => "LSH"
        case _: PermutationLsh                                        => "Permutation LSH"
      }
    }

    def apply(b: BenchmarkResult): AggregateResult = {
      new AggregateResult(
        dataset = b.dataset.name,
        similarity = b.query.similarity.toString,
        algorithm = algorithmName(b.query),
        k = b.k,
        mapping = b.mapping,
        shards = b.shards,
        query = b.query.withVec(Vec.Empty()),
        parallelQueries = b.parallelQueries,
        recall = (b.queryResults.map(_.recall).sum / b.queryResults.length).toFloat,
        queriesPerSecond = b.queriesPerSecond
      )
    }
  }

  object Experiment {

    private val vecName: String = "vec"

    /**
      * Alias to gridsearch method for multiple datasets.
      */
    def gridsearch(datasets: Dataset*): Seq[Experiment] = datasets.flatMap(gridsearch)

    /**
      * Returns reasonable Experiments for doing a gridsearch over parameters for the given dataset.
      */
    def gridsearch(dataset: Dataset): Seq[Experiment] = dataset match {

      case Dataset.AnnbMnist | Dataset.AnnbFashionMnist =>
        for {
          tables <- Seq(50, 75, 100)
          hashesPerTable <- Seq(2, 3, 4)
          width <- 5 to 8
        } yield
          Experiment(
            dataset,
            Mapping.DenseFloat(dataset.dims),
            NearestNeighborsQuery.Exact(vecName, Similarity.L2),
            Mapping.L2Lsh(dataset.dims, L = tables, k = hashesPerTable, w = width),
            for {
              candidates <- Seq(1000, 5000)
              probes <- Seq(0, 3, 6, 9)
            } yield Query(NearestNeighborsQuery.L2Lsh(vecName, candidates, probes), 100)
          )

      case Dataset.AnnbSift =>
        for {
          tables <- Seq(50, 75, 100)
          hashesPerTable <- Seq(2, 3, 4)
          width <- Seq(1, 2, 3)
        } yield
          Experiment(
            dataset,
            Mapping.DenseFloat(dataset.dims),
            NearestNeighborsQuery.Exact(vecName, Similarity.L2),
            Mapping.L2Lsh(dataset.dims, L = tables, k = hashesPerTable, w = width),
            for {
              candidates <- Seq(1000, 5000, 10000)
              probes <- 0 to math.pow(hashesPerTable, 3).toInt.min(9) by 3
            } yield Query(NearestNeighborsQuery.L2Lsh(vecName, candidates, probes), 100)
          )

      case Dataset.AnnbGlove100 =>
        val projections = for {
          tables <- Seq(50, 100, 125)
          hashesPerTable <- Seq(6, 9)
        } yield
          Experiment(
            dataset,
            Mapping.DenseFloat(dataset.dims),
            NearestNeighborsQuery.Exact(vecName, Similarity.Angular),
            Mapping.AngularLsh(dataset.dims, tables, hashesPerTable),
            for {
              candidates <- Seq(1000, 5000)
            } yield Query(NearestNeighborsQuery.AngularLsh(vecName, candidates), 100)
          )
        val permutations = for {
          k <- Seq(10, 25, 50, 75)
          rep <- Seq(false)
        } yield
          Experiment(
            dataset,
            Mapping.DenseFloat(dataset.dims),
            NearestNeighborsQuery.Exact(vecName, Similarity.Angular),
            Mapping.PermutationLsh(dataset.dims, k, repeating = rep),
            for {
              candidates <- Seq(1000, 5000, 10000)
            } yield Query(NearestNeighborsQuery.PermutationLsh(vecName, Similarity.Angular, candidates), 100)
          )
        projections ++ permutations

      case _ => Seq.empty
    }

  }

  object codecs {
    private implicit val mappingCodec: Codec[Mapping] = ElasticsearchCodec.mapping
    private implicit val nnqCodec: Codec[NearestNeighborsQuery] = ElasticsearchCodec.nearestNeighborsQuery
    implicit val queryCodec: Codec[Query] = deriveCodec
    implicit val datasetCodec: Codec[Dataset] = deriveCodec
    implicit val experimentCodec: Codec[Experiment] = deriveCodec
    implicit val singleResultCodec: Codec[QueryResult] = deriveCodec
    implicit val resultCodec: Codec[BenchmarkResult] = deriveCodec
    implicit val aggregateResultCodec: Codec[AggregateResult] = deriveCodec
  }

}
