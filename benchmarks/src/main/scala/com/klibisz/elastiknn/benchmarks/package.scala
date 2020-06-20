package com.klibisz.elastiknn

import java.util.Base64

import com.klibisz.elastiknn.api.Mapping.{AngularLsh, DenseFloat, HammingLsh, JaccardLsh, L2Lsh, SparseBool, SparseIndexed}
import com.klibisz.elastiknn.api._
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.{Codec, Encoder}
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.math3.stat.descriptive.rank.Percentile
import zio.Has

import scala.language.implicitConversions

package object benchmarks {

  type DatasetClient = Has[DatasetClient.Service]
  type ResultClient = Has[ResultClient.Service]
  type ElastiknnZioClient = Has[ElastiknnZioClient.Service]

  sealed abstract class Dataset(val dims: Int) {
    final def name: String = this.getClass.getSimpleName.toLowerCase.replace("$", "")
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
    case object AnnbGlove100 extends Dataset(100)
    case object AnnbKosarak extends Dataset(27983)
    case object AnnbMnist extends Dataset(784)
    case object AnnbNyt extends Dataset(256)
    case object AnnbSift extends Dataset(128)
    case class RandomDenseFloat(override val dims: Int = 1024, count: Int = 10000) extends Dataset(dims)
    case class RandomSparseBool(override val dims: Int = 4096, count: Int = 10000) extends Dataset(dims)
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
                                   parallelism: Int,
                                   durationMillis: Long = 0,
                                   queryResults: Seq[QueryResult]) {
    override def toString: String = s"Result($dataset, $mapping, $query, $k, $parallelism, $durationMillis, ...)"
  }

  final case class AggregateResult(dataset: String,
                                   algorithm: String,
                                   k: Int,
                                   mappingJson: String,
                                   queryJson: String,
                                   recallP10: Double,
                                   recallP50: Double,
                                   recallP90: Double,
                                   durationP10: Double,
                                   durationP50: Double,
                                   durationP90: Double)
  object AggregateResult {

    val header = Seq("dataset",
                     "algorithm",
                     "k",
                     "mappingJson",
                     "queryJson",
                     "recallP10",
                     "recallP50",
                     "recallP90",
                     "durationP10",
                     "durationP50",
                     "durationP90")

    private def mappingToAlgorithmName(m: Mapping): String = m match {
      case _: SparseBool                                            => "exact"
      case _: DenseFloat                                            => "exact"
      case _: SparseIndexed                                         => "sparse indexed"
      case _: JaccardLsh | _: HammingLsh | _: AngularLsh | _: L2Lsh => "lsh"
    }

    def apply(benchmarkResult: BenchmarkResult): AggregateResult = {
      val ptile = new Percentile()
      val recalls = benchmarkResult.queryResults.map(_.recall).toArray
      val durations = benchmarkResult.queryResults.map(_.duration.toDouble).toArray
      AggregateResult(
        benchmarkResult.dataset.name,
        mappingToAlgorithmName(benchmarkResult.mapping),
        benchmarkResult.k,
        ElasticsearchCodec.encode(benchmarkResult.mapping).noSpaces,
        ElasticsearchCodec.encode(benchmarkResult.query).noSpaces,
        ptile.evaluate(recalls, 0.1),
        ptile.evaluate(recalls, 0.5),
        ptile.evaluate(recalls, 0.9),
        ptile.evaluate(durations, 0.1),
        ptile.evaluate(durations, 0.5),
        ptile.evaluate(durations, 0.9),
      )
    }
  }

  object Experiment {
    import Dataset._

    private val vecName: String = "vec"
    private val empty: Vec = Vec.Empty()
    val defaultKs: Seq[Int] = Seq(10, 100)

    def l2(dataset: Dataset, ks: Seq[Int] = defaultKs): Seq[Experiment] = {
      val lsh = for {
        b <- 100 to 350 by 50
        r <- 1 to 3
        w <- 1 to 3
      } yield
        Experiment(
          dataset,
          Mapping.DenseFloat(dataset.dims),
          NearestNeighborsQuery.Exact(vecName, empty, Similarity.L2),
          Mapping.L2Lsh(dataset.dims, b, r, w),
          for {
            k <- ks
            m <- Seq(1, 2, 10)
          } yield Query(NearestNeighborsQuery.L2Lsh(vecName, empty, m * k), k)
        )
      lsh
    }

    def angular(dataset: Dataset, ks: Seq[Int] = defaultKs): Seq[Experiment] = {
      val lsh = for {
        b <- 100 to 350 by 50
        r <- 1 to 3
      } yield
        Experiment(
          dataset,
          Mapping.DenseFloat(dataset.dims),
          NearestNeighborsQuery.Exact(vecName, empty, Similarity.Angular),
          Mapping.AngularLsh(dataset.dims, b, r),
          for {
            k <- ks
            m <- Seq(1, 2, 10)
          } yield Query(NearestNeighborsQuery.AngularLsh(vecName, empty, m * k), k)
        )
      lsh
    }

    def hamming(dataset: Dataset, ks: Seq[Int] = defaultKs): Seq[Experiment] = {
      val sparseIndexed = Seq(
        Experiment(
          dataset,
          Mapping.SparseBool(dataset.dims),
          NearestNeighborsQuery.Exact(vecName, empty, Similarity.Hamming),
          Mapping.SparseIndexed(dataset.dims),
          for {
            k <- ks
          } yield Query(NearestNeighborsQuery.SparseIndexed(vecName, empty, Similarity.Hamming), k)
        )
      )
      val lsh = for {
        bitsProp <- Seq(0.1, 0.3, 0.5, 0.7, 0.9)
      } yield
        Experiment(
          dataset,
          Mapping.SparseBool(dataset.dims),
          NearestNeighborsQuery.Exact(vecName, empty, Similarity.Hamming),
          Mapping.HammingLsh(dataset.dims, (bitsProp * dataset.dims).toInt),
          for {
            k <- ks
            m <- Seq(1, 2, 10, 50)
          } yield Query(NearestNeighborsQuery.HammingLsh(vecName, empty, k * m), k)
        )
      sparseIndexed ++ lsh
    }

    def jaccard(dataset: Dataset, ks: Seq[Int] = defaultKs): Seq[Experiment] = {
      val sparseIndexed = Seq(
        Experiment(
          dataset,
          Mapping.SparseBool(dataset.dims),
          NearestNeighborsQuery.Exact(vecName, empty, Similarity.Jaccard),
          Mapping.SparseIndexed(dataset.dims),
          for {
            k <- ks
          } yield Query(NearestNeighborsQuery.SparseIndexed(vecName, empty, Similarity.Jaccard), k)
        )
      )
      sparseIndexed
    }

    // TODO: add AmazonMixed, AmazonHomePHash, EnglishWikiLSA
    val defaults: Seq[Experiment] = Seq(
      l2(AmazonHome),
      angular(AmazonHomeUnit),
      angular(AmazonMixedUnit),
      angular(AnnbDeep1b),
      l2(AnnbFashionMnist),
      l2(AnnbGist),
      angular(AnnbGlove100),
      jaccard(AnnbKosarak),
      l2(AnnbMnist),
      angular(AnnbNyt),
      l2(AnnbSift)
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
  }

}
