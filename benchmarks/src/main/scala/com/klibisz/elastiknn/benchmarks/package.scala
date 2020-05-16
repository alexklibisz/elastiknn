package com.klibisz.elastiknn

import com.klibisz.elastiknn.api._
import io.circe.Codec
import io.circe.generic.semiauto._
import zio.Has

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

  final case class MappingAndQueries(mapping: Mapping, queries: Seq[Query])

  object MappingAndQueries {
    def apply(mapping: Mapping, query: Query): MappingAndQueries = MappingAndQueries(mapping, Seq(query))
  }

  final case class Experiment(dataset: Dataset, exact: MappingAndQueries, maqs: Seq[MappingAndQueries], shards: Int = 1)

  final case class Result(dataset: Dataset, mapping: Mapping, query: NearestNeighborsQuery, k: Int, recalls: Seq[Double], durations: Seq[Long]) {
    override def toString: String = s"Result($dataset, $mapping, $query, $k, ..., ...)"
  }

  object Experiment {
    import Dataset._

    private val vectorField: String = "vec"
    private val ks: Seq[Int] = Seq(10, 100)

    def l2(dataset: Dataset): Experiment =
      Experiment(
        dataset,
        MappingAndQueries(Mapping.DenseFloat(dataset.dims), Query(NearestNeighborsQuery.Exact(vectorField, Vec.Empty(), Similarity.L2), ks.max)),
        for {
          k <- ks
          b <- Seq(10) ++ (50 to 300 by 50)
          r <- 1 to 3
          w <- 1 to 5
        } yield MappingAndQueries(Mapping.L2Lsh(dataset.dims, b, r, w), Seq(1, 2, 10).map(m => Query(NearestNeighborsQuery.L2Lsh(vectorField, Vec.Empty(), m * k), k)))
      )

    def angular(dataset: Dataset): Experiment = Experiment(
      dataset,
      MappingAndQueries(Mapping.DenseFloat(dataset.dims), Query(NearestNeighborsQuery.Exact(vectorField, Vec.Empty(), Similarity.Angular), ks.max)),
      for {
        k <- ks
        b <- Seq(10) ++ (50 to 300 by 50)
        r <- 1 to 3
      } yield
        MappingAndQueries(
          Mapping.AngularLsh(dataset.dims, b, r),
          Seq(1, 2, 10).map(m => Query(NearestNeighborsQuery.AngularLsh(vectorField, Vec.Empty(), m * k), k))
        )
    )

    def hamming(dataset: Dataset): Experiment =
      Experiment(
        dataset,
        MappingAndQueries(Mapping.SparseBool(dataset.dims), Query(NearestNeighborsQuery.Exact(vectorField, Vec.Empty(), Similarity.Hamming), ks.max)),
        (for {
          k <- ks
          bitsProp <- Seq(0.1, 0.3, 0.5, 0.7, 0.9)
        } yield
          MappingAndQueries(
            Mapping.HammingLsh(dataset.dims, (dataset.dims * bitsProp).toInt),
            Seq(1, 2, 5, 10).map(m => Query(NearestNeighborsQuery.HammingLsh(vectorField, Vec.Empty(), k * m), k))
          )) :+ MappingAndQueries(Mapping.SparseIndexed(dataset.dims), ks.map(k => Query(NearestNeighborsQuery.SparseIndexed(vectorField, Vec.Empty(), Similarity.Hamming), k)))
      )

    def jaccard(dataset: Dataset): Experiment = Experiment(
      dataset,
      MappingAndQueries(Mapping.SparseBool(dataset.dims), Query(NearestNeighborsQuery.Exact(vectorField, Vec.Empty(), Similarity.Jaccard), ks.max)),
      (for {
        k <- ks
        b <- Seq(10) ++ (50 to 300 by 50)
        r <- 1 to 3
      } yield
        MappingAndQueries(
          Mapping.JaccardLsh(dataset.dims, b, r),
          Seq(1, 2, 10).map(m => Query(NearestNeighborsQuery.JaccardLsh(vectorField, Vec.Empty(), m * k), k))
        )) :+ MappingAndQueries(Mapping.SparseIndexed(dataset.dims), ks.map(k => Query(NearestNeighborsQuery.SparseIndexed(vectorField, Vec.Empty(), Similarity.Jaccard), k)))
    )

    val defaults = Seq(
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
    )

  }

  object codecs {
    private implicit val mapping: Codec[Mapping] = ElasticsearchCodec.mapping
    private implicit val nnq: Codec[NearestNeighborsQuery] = ElasticsearchCodec.nearestNeighborsQuery
    implicit val query: Codec[Query] = deriveCodec
    implicit val dataset: Codec[Dataset] = deriveCodec
    implicit val mappingAndQueries: Codec[MappingAndQueries] = deriveCodec
    implicit val experiment: Codec[Experiment] = deriveCodec
    implicit val result: Codec[Result] = deriveCodec
  }

}
