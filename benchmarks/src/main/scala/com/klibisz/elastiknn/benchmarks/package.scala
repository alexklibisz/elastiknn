package com.klibisz.elastiknn

import com.klibisz.elastiknn.api._
import io.circe.Codec
import io.circe.generic.semiauto._
import zio.Has

import scala.language.implicitConversions

package object benchmarks {

  type DatasetClient = Has[DatasetClient.Service]
  type ResultClient = Has[ResultClient.Service]
  type ElastiknnZioClient = Has[ElastiknnZioClient.Service]

  sealed abstract class Dataset(val dims: Int) {
    final def name: String = this.toString.toLowerCase
  }
  object Dataset {
    case object AmazonHome extends Dataset(4096)
    case object AmazonHomeUnit extends Dataset(4096)
    case object AmazonHomePhash extends Dataset(4096)
    case object AmazonMixed extends Dataset(4096)
    case object AmazonMixedUnit extends Dataset(4096)
    case object AmazonMixedPhash extends Dataset(4096)
    case object EnglishWikiLsa extends Dataset(1024)
    case object AnnbDeep1b extends Dataset(96)
    case object AnnbFashionMnist extends Dataset(784)
    case object AnnbGist extends Dataset(960)
    case object AnnbGlove25 extends Dataset(25)
    case object AnnbGlove50 extends Dataset(50)
    case object AnnbGlove100 extends Dataset(100)
    case object AnnbGlove200 extends Dataset(200)
    case object AnnbKosarak extends Dataset(27983)
    case object AnnbMnist extends Dataset(784)
    case object AnnbNyt extends Dataset(256)
    case object AnnbSift extends Dataset(128)
  }

  final case class Query(nnq: NearestNeighborsQuery, k: Int)

  final case class Experiment(dataset: Dataset, exactMapping: Mapping, exactQuery: NearestNeighborsQuery, testMapping: Mapping, testQueries: Seq[Query])

  final case class Result(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int, recalls: Seq[Double], durations: Seq[Long]) {
    override def toString: String = s"Result($dataset, $mapping, $query, $k, ..., ...)"
  }

  object Experiment {
    import Dataset._

    private val vecName: String = "vec"
    private val empty: Vec = Vec.Empty()
    val defaultKs: Seq[Int] = Seq(10, 100)

    def l2(dataset: Dataset, ks: Seq[Int] = defaultKs): Seq[Experiment] = {
      val lsh = for {
        b <- Seq(10) ++ (50 to 300 by 50)
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
        b <- Seq(10) ++ (50 to 300 by 50)
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
            m <- Seq(1, 2, 10)
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

    val defaults: Seq[Experiment] = Seq(
      l2(AmazonHome),
      l2(AmazonMixed),
      angular(AmazonHomeUnit),
      angular(AmazonMixedUnit),
      hamming(AmazonHomePhash),
      hamming(AmazonMixedPhash),
      angular(EnglishWikiLsa),
      angular(AnnbDeep1b),
      l2(AnnbFashionMnist),
      l2(AnnbGist),
      angular(AnnbGlove25),
      angular(AnnbGlove50),
      angular(AnnbGlove100),
      angular(AnnbGlove200),
      jaccard(AnnbKosarak),
      l2(AnnbMnist),
      angular(AnnbNyt),
      l2(AnnbSift)
    ).flatten

  }

  object codecs {
    private implicit val mapping: Codec[Mapping] = ElasticsearchCodec.mapping
    private implicit val nnq: Codec[NearestNeighborsQuery] = ElasticsearchCodec.nearestNeighborsQuery
    implicit val query: Codec[Query] = deriveCodec
    implicit val dataset: Codec[Dataset] = deriveCodec
    implicit val experiment: Codec[Experiment] = deriveCodec
    implicit val result: Codec[Result] = deriveCodec
  }

}
