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

  final case class Query(nnq: NearestNeighborsQuery, k: Int) {
    def algorithmName: String = {
      import NearestNeighborsQuery._
      nnq match {
        case _: Exact                                                 => "Exact"
        case _: SparseIndexed                                         => "Sparse Indexed"
        case _: HammingLsh | _: JaccardLsh | _: AngularLsh | _: L2Lsh => "LSH"
        case _: PermutationLsh                                        => "Permutation LSH"
      }
    }
  }

  final case class QueryResult(scores: Seq[Float], duration: Long)

  final case class Experiment(dataset: Dataset,
                              mapping: Mapping,
                              queries: Seq[Query],
                              shards: Int = 1,
                              replicas: Int = 0,
                              parallelQueries: Int = 1,
                              esNodes: Int = 1,
                              esCoresPerNode: Int = 1,
                              esMemoryGb: Int = 4,
                              warmupQueries: Int = 200,
                              minWarmupRounds: Int = 10,
                              maxWarmupRounds: Int = 10) {

    def uuid: String = DigestUtils.sha256Hex(this.hashCode.toString).toLowerCase

    override def toString: String =
      s"""
        |Experiment(
        | dataset = ${dataset},
        | mapping = ${mapping},
        | queries = ${queries},
        | shards = ${shards}
        | replicas = ${replicas}
        | parallelQueries = ${parallelQueries}
        | esNodes = ${esNodes}
        | esCoresPerNode = ${esCoresPerNode}
        | esMemoryGb = ${esMemoryGb}
        | warmupQueries = ${warmupQueries}
        | minWarmupRounds = ${minWarmupRounds}
        | maxWarmupRounds = ${maxWarmupRounds}
        |)""".stripMargin
  }

  final case class BenchmarkResult(dataset: Dataset,
                                   similarity: Similarity,
                                   algorithm: String,
                                   mapping: Mapping,
                                   query: NearestNeighborsQuery,
                                   k: Int,
                                   shards: Int,
                                   replicas: Int,
                                   parallelQueries: Int,
                                   esNodes: Int,
                                   esCoresPerNode: Int,
                                   esMemoryGb: Int,
                                   warmupQueries: Int,
                                   minWarmupRounds: Int,
                                   maxWarmupRounds: Int,
                                   recall: Float,
                                   queriesPerSecond: Float,
                                   durationMillis: Long) {
    lazy val md5sum: String = DigestUtils.md5Hex(codecs.resultCodec(this).noSpaces).toLowerCase
  }

  object codecs {
    import ElasticsearchCodec._
    private implicit val mappingCodec: Codec[Mapping] = ElasticsearchCodec.mapping
    private implicit val nnqCodec: Codec[NearestNeighborsQuery] = ElasticsearchCodec.nearestNeighborsQuery
    implicit val queryCodec: Codec[Query] = deriveCodec
    implicit val datasetCodec: Codec[Dataset] = deriveCodec
    implicit val experimentCodec: Codec[Experiment] = deriveCodec
    implicit val resultCodec: Codec[BenchmarkResult] = deriveCodec
  }

}
