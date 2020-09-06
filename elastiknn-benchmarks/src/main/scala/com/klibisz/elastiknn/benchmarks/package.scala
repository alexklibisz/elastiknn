package com.klibisz.elastiknn

import java.time.LocalDate

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
    case class S3Pointer(bucket: String, prefix: String, override val dims: Int) extends Dataset(dims)
  }

  final case class Query(nnq: NearestNeighborsQuery, k: Int)

  final case class Experiment(dataset: Dataset,
                              exactMapping: Mapping,
                              exactQuery: NearestNeighborsQuery,
                              testMapping: Mapping,
                              testQueries: Seq[Query]) {
    def md5sum: String = DigestUtils.md5Hex(codecs.experimentCodec(this).noSpaces).toLowerCase
  }

  final case class QueryResult(neighbors: Seq[String], duration: Long, recall: Double = Double.NaN)

  final case class BenchmarkResult(dataset: Dataset,
                                   mapping: Mapping,
                                   query: NearestNeighborsQuery,
                                   k: Int,
                                   shards: Int,
                                   parallelQueries: Int,
                                   durationMillis: Long = 0,
                                   queryResults: Array[QueryResult]) {
    override def toString: String = s"Result($dataset, $mapping, $query, $k, $shards, $parallelQueries, $durationMillis, ...)"
  }

  final case class AggregateResult(date: LocalDate,
                                   hash: String,
                                   branch: String,
                                   host: String,
                                   dataset: String,
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
      "date",
      "hash",
      "branch",
      "host",
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

    def apply(benchmarkResult: BenchmarkResult): AggregateResult = {
      new AggregateResult(
        date = LocalDate.now(),
        hash = BuildConfig.GIT_HASH,
        branch = BuildConfig.GIT_BRANCH,
        host = BuildConfig.HOST_NAME,
        dataset = benchmarkResult.dataset.name,
        similarity = benchmarkResult.query.similarity.toString,
        algorithm = algorithmName(benchmarkResult.query),
        k = benchmarkResult.k,
        mapping = benchmarkResult.mapping,
        shards = benchmarkResult.shards,
        query = benchmarkResult.query.withVec(Vec.Empty()),
        parallelQueries = benchmarkResult.parallelQueries,
        recall = (benchmarkResult.queryResults.map(_.recall).sum / benchmarkResult.queryResults.length).toFloat,
        queriesPerSecond = benchmarkResult.queryResults.length * 1f / benchmarkResult.durationMillis * 1000L
      )
    }
  }

  object Experiment {
    import Dataset._

    private val vecName: String = "vec"
    val defaultKs: Seq[Int] = Seq(100)

    def l2(dataset: Dataset, ks: Seq[Int] = defaultKs): Seq[Experiment] = {
      val lsh = for {
        tables <- Seq(50, 75, 100, 200, 250)
        hashesPerTable <- 1 to 3
        width <- 1 to 3
      } yield
        Experiment(
          dataset,
          Mapping.DenseFloat(dataset.dims),
          NearestNeighborsQuery.Exact(vecName, Similarity.L2),
          Mapping.L2Lsh(dataset.dims, L = tables, k = hashesPerTable, r = width),
          for {
            k <- ks
            candidateMultiple <- Seq(10, 20, 50)
            probes <- 0 to math.pow(hashesPerTable, 3).toInt.min(9) by 3
          } yield Query(NearestNeighborsQuery.L2Lsh(vecName, candidateMultiple * k, probes), k)
        )
      lsh
    }

    def angular(dataset: Dataset, ks: Seq[Int] = defaultKs): Seq[Experiment] = {
      val classic = for {
        b <- Seq(50, 75, 100)
        r <- 1 to 3
      } yield
        Experiment(
          dataset,
          Mapping.DenseFloat(dataset.dims),
          NearestNeighborsQuery.Exact(vecName, Similarity.Angular),
          Mapping.AngularLsh(dataset.dims, b, r),
          for {
            k <- ks
            m <- Seq(10, 20, 50)
          } yield Query(NearestNeighborsQuery.AngularLsh(vecName, m * k), k)
        )

      val permutation = for {
        m <- Seq(0.1, 0.2, 0.5)
        r <- Seq(true, false)
      } yield
        Experiment(
          dataset,
          Mapping.DenseFloat(dataset.dims),
          NearestNeighborsQuery.Exact(vecName, Similarity.Angular),
          Mapping.PermutationLsh(dataset.dims, (m * dataset.dims).toInt, r),
          for {
            k <- ks
            m <- Seq(10, 20, 50)
          } yield Query(NearestNeighborsQuery.PermutationLsh(vecName, Similarity.Angular, m * k), k)
        )

      classic ++ permutation
    }

    def hamming(dataset: Dataset, ks: Seq[Int] = defaultKs): Seq[Experiment] = {
      val sparseIndexed = Seq(
        Experiment(
          dataset,
          Mapping.SparseBool(dataset.dims),
          NearestNeighborsQuery.Exact(vecName, Similarity.Hamming),
          Mapping.SparseIndexed(dataset.dims),
          for {
            k <- ks
          } yield Query(NearestNeighborsQuery.SparseIndexed(vecName, Similarity.Hamming), k)
        )
      )
      val lsh = for {
        l <- 100 to 350 by 50
        kProp <- Seq(0.01, 0.1, 0.20)
      } yield
        Experiment(
          dataset,
          Mapping.SparseBool(dataset.dims),
          NearestNeighborsQuery.Exact(vecName, Similarity.Hamming),
          Mapping.HammingLsh(dataset.dims, L = l, k = (kProp * dataset.dims).toInt),
          for {
            k <- ks
            m <- Seq(1, 2, 10, 50)
          } yield Query(NearestNeighborsQuery.HammingLsh(vecName, k * m), k)
        )
      sparseIndexed ++ lsh
    }

    def jaccard(dataset: Dataset, ks: Seq[Int] = defaultKs): Seq[Experiment] = {
      val sparseIndexed = Seq(
        Experiment(
          dataset,
          Mapping.SparseBool(dataset.dims),
          NearestNeighborsQuery.Exact(vecName, Similarity.Jaccard),
          Mapping.SparseIndexed(dataset.dims),
          for {
            k <- ks
          } yield Query(NearestNeighborsQuery.SparseIndexed(vecName, Similarity.Jaccard), k)
        )
      )
      sparseIndexed
    }

    // TODO: add AmazonMixed, AmazonHomePHash, EnglishWikiLSA
    val defaults: Seq[Experiment] = Seq(
      l2(AnnbSift)
//      angular(AnnbGlove100)
//      l2(AmazonHome),
//      angular(AmazonHomeUnit),
//      angular(AmazonMixedUnit),
//      angular(AnnbDeep1b),
//      l2(AnnbFashionMnist),
//      l2(AnnbGist),
//      angular(AnnbGlove100),
//      jaccard(AnnbKosarak),
//      l2(AnnbMnist),
//      angular(AnnbNyt),
//      l2(AnnbSift)
    ).flatten

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
