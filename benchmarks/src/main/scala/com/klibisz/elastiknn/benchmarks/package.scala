package com.klibisz.elastiknn

import com.klibisz.elastiknn.api._

package object benchmarks {

  sealed trait Dataset {
    final def name: String = this.toString.toLowerCase
  }
  object Dataset {
    case object AmazonHome extends Dataset
    case object AmazonHomeUnit extends Dataset
    case object AmazonHomePhash extends Dataset
    case object AmazonMixed extends Dataset
    case object AmazonMixedUnit extends Dataset
    case object AmazonMixedPhash extends Dataset
    case object EnglishWikiLsa extends Dataset
    case object AnnbDeep1b extends Dataset
    case object AnnbFashionMnist extends Dataset
    case object AnnbGist extends Dataset
    case object AnnbGlove extends Dataset
    case object AnnbGlove25 extends Dataset
    case object AnnbGlove50 extends Dataset
    case object AnnbGlove100 extends Dataset
    case object AnnbGlove200 extends Dataset
    case object AnnbKosarak extends Dataset
    case object AnnbMnist extends Dataset
    case object AnnbNyt extends Dataset
    case object AnnbSift extends Dataset
  }

  final case class MappingAndQueries(mappingFunc: Int => Mapping, queryFuncs: Seq[(String, Vec, Int) => NearestNeighborsQuery])

  object MappingAndQueries {
    def apply(mappingFunc: Int => Mapping, queryFunc: (String, Vec, Int) => NearestNeighborsQuery): MappingAndQueries =
      MappingAndQueries(mappingFunc, Seq(queryFunc))
  }

  final case class Experiment(dataset: Dataset, exact: MappingAndQueries, maqs: Seq[MappingAndQueries], shards: Int = 1)

  object Experiment {
    import Dataset._

    def l2(dataset: Dataset): Experiment = Experiment(
      dataset,
      MappingAndQueries(d => Mapping.DenseFloat(d), (f, v, _) => NearestNeighborsQuery.Exact(f, v, Similarity.L2)),
      for {
        b <- Seq(10) ++ (50 to 300 by 50)
        r <- 1 to 3
        w <- 1 to 5
      } yield
        MappingAndQueries(
          dims => Mapping.L2Lsh(dims, b, r, w),
          Seq(1, 2, 10).map(m => (f: String, v: Vec, k: Int) => NearestNeighborsQuery.L2Lsh(f, v, m * k))
        )
    )

    def angular(dataset: Dataset): Experiment = Experiment(
      dataset,
      MappingAndQueries(d => Mapping.DenseFloat(d), (f, v, _) => NearestNeighborsQuery.Exact(f, v, Similarity.Angular)),
      for {
        b <- Seq(10) ++ (50 to 300 by 50)
        r <- 1 to 3
      } yield
        MappingAndQueries(
          d => Mapping.AngularLsh(d, b, r),
          Seq(1, 2, 10).map(m => (f: String, v: Vec, k: Int) => NearestNeighborsQuery.AngularLsh(f, v, m * k))
        )
    )

    def hamming(dataset: Dataset): Experiment = Experiment(
      dataset,
      MappingAndQueries(d => Mapping.SparseBool(d), (f, v, _) => NearestNeighborsQuery.Exact(f, v, Similarity.Jaccard)),
      MappingAndQueries(d => Mapping.SparseIndexed(d), (f, v, _) => NearestNeighborsQuery.SparseIndexed(f, v, Similarity.Hamming)) +:
        Seq(0.1, 0.5, 0.9).map { bitsProp =>
        MappingAndQueries(d => Mapping.HammingLsh(d, (d * bitsProp).toInt),
                          Seq(1, 2, 10).map(m => (f: String, v: Vec, k: Int) => NearestNeighborsQuery.HammingLsh(f, v, k * m)))
      }
    )

    def jaccard(dataset: Dataset): Experiment = Experiment(
      dataset,
      MappingAndQueries(d => Mapping.SparseBool(d), (f, v, _) => NearestNeighborsQuery.Exact(f, v, Similarity.Jaccard)),
      for {
        b <- Seq(10) ++ (50 to 300 by 50)
        r <- 1 to 3
      } yield
        MappingAndQueries(
          d => Mapping.JaccardLsh(d, b, r),
          Seq(1, 2, 10).map(m => (f: String, v: Vec, k: Int) => NearestNeighborsQuery.JaccardLsh(f, v, m * k))
        )
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

  final case class Result(dataset: Dataset,
                          mapping: Mapping,
                          query: NearestNeighborsQuery,
                          k: Int,
                          recalls: Seq[Double],
                          durations: Seq[Long])

}
