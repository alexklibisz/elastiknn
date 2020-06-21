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
                                   similarity: String,
                                   algorithm: String,
                                   k: Int,
                                   recallP10: Float,
                                   durationP10: Float,
                                   recallP50: Float,
                                   durationP50: Float,
                                   recallP90: Float,
                                   durationP90: Float,
                                   mappingJson: String,
                                   queryJson: String)
  object AggregateResult {

    val header = Seq(
      "dataset",
      "similarity",
      "algorithm",
      "k",
      "recallP10",
      "durationP10",
      "recallP50",
      "durationP50",
      "recallP90",
      "durationP90",
      "mapping",
      "query"
    )

    private def mappingToAlgorithmName(m: Mapping): String = m match {
      case _: SparseBool                                            => s"Exact"
      case _: DenseFloat                                            => "exact"
      case _: SparseIndexed                                         => "sparse indexed"
      case _: JaccardLsh | _: HammingLsh | _: AngularLsh | _: L2Lsh => "lsh"
    }

    private def queryToSimilarityName(q: NearestNeighborsQuery): String = q.similarity.toString

    def apply(benchmarkResult: BenchmarkResult): AggregateResult = {
      val ptile = new Percentile()
      val recalls = benchmarkResult.queryResults.map(_.recall).toArray
      val durations = benchmarkResult.queryResults.map(_.duration.toDouble).toArray
      new AggregateResult(
        benchmarkResult.dataset.name,
        queryToSimilarityName(benchmarkResult.query),
        mappingToAlgorithmName(benchmarkResult.mapping),
        benchmarkResult.k,
        ptile.evaluate(recalls, 0.1).toFloat,
        ptile.evaluate(durations, 0.1).toFloat,
        ptile.evaluate(recalls, 0.5).toFloat,
        ptile.evaluate(durations, 0.5).toFloat,
        ptile.evaluate(recalls, 0.9).toFloat,
        ptile.evaluate(durations, 0.9).toFloat,
        ElasticsearchCodec.encode(benchmarkResult.mapping).noSpaces,
        ElasticsearchCodec.encode(benchmarkResult.query).noSpaces
      )
    }
  }

  object Experiment {
    import Dataset._

    private val vecName: String = "vec"
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
          NearestNeighborsQuery.Exact(vecName, Similarity.L2),
          Mapping.L2Lsh(dataset.dims, b, r, w),
          for {
            k <- ks
            m <- Seq(1, 2, 10)
          } yield Query(NearestNeighborsQuery.L2Lsh(vecName, m * k), k)
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
          NearestNeighborsQuery.Exact(vecName, Similarity.Angular),
          Mapping.AngularLsh(dataset.dims, b, r),
          for {
            k <- ks
            m <- Seq(1, 2, 10)
          } yield Query(NearestNeighborsQuery.AngularLsh(vecName, m * k), k)
        )
      lsh
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
        bitsProp <- Seq(0.1, 0.3, 0.5, 0.7, 0.9)
      } yield
        Experiment(
          dataset,
          Mapping.SparseBool(dataset.dims),
          NearestNeighborsQuery.Exact(vecName, Similarity.Hamming),
          Mapping.HammingLsh(dataset.dims, (bitsProp * dataset.dims).toInt),
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
