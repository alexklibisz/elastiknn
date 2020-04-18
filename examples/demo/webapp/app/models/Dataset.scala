package models

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.client.ElastiknnRequests
import com.sksamuel.elastic4s.Hit
import com.sksamuel.elastic4s.requests.indexes.PutMappingBuilderFn
import com.sksamuel.elastic4s.requests.mappings.PutMappingRequest
import io.circe.generic.semiauto._
import io.circe.{Encoder, Json}

import scala.util.Try

case class Dataset(prettyName: String, sourceName: String, permalink: String, examples: Seq[Example]) {
  def parseHit(hit: Hit): Try[SearchResult] =
    if (sourceName.startsWith("mnist")) SearchResult.Image.parseHit(hit) else SearchResult.WordVector.parseHit(hit)
}

case class Example(name: String, index: String, field: String, mapping: PutMappingRequest, query: NearestNeighborsQuery)

object Example {
  implicit val encodePutMapping: Encoder[PutMappingRequest] = (a: PutMappingRequest) => Json.fromString(PutMappingBuilderFn(a).string())
  implicit val encodeQuery: Encoder[NearestNeighborsQuery] = ElasticsearchCodec.nearestNeighborsQuery
  implicit val encoder: Encoder[Example] = deriveEncoder[Example]
}

object Dataset extends ElastiknnRequests {

  private def example(name: String, index: String, mapping: Mapping, mkQuery: (String, Vec) => NearestNeighborsQuery): Example =
    Example(
      name,
      index,
      "vec",
      putMapping(index, "vec", mapping),
      mkQuery("vec", Vec.Indexed(index, "1", "vec"))
    )

  val defaults: Seq[Dataset] = Seq(
    Dataset(
      "MNIST Digits with Jaccard Similarity",
      "mnist_binary",
      "mnist-jaccard",
      Seq(
        example("Exact",
                "mnist-jaccard-exact",
                Mapping.SparseBool(784),
                (field, vec) => NearestNeighborsQuery.Exact(field, vec, Similarity.Jaccard)),
        example("Sparse Indexed",
                "mnist-jaccard-sparse-indexed",
                Mapping.SparseIndexed(784),
                (f, v) => NearestNeighborsQuery.SparseIndexed(f, v, Similarity.Jaccard)),
        example("Jaccard LSH 1",
                "mnist-jaccard-lsh-1",
                Mapping.JaccardLsh(784, 100, 1),
                (f, v) => NearestNeighborsQuery.JaccardLsh(f, v, 100)),
        example("Jaccard LSH 1",
                "mnist-jaccard-lsh-2",
                Mapping.JaccardLsh(784, 100, 2),
                (f, v) => NearestNeighborsQuery.JaccardLsh(f, v, 100)),
      )
    ),
    Dataset(
      "MNIST Digits with Hamming Similarity",
      "mnist_binary",
      "mnist-hamming",
      Seq(
        example("Exact",
                "mnist-hamming-exact",
                Mapping.SparseBool(784),
                (field, vec) => NearestNeighborsQuery.Exact(field, vec, Similarity.Hamming)),
        example("Sparse Indexed",
                "mnist-hamming-sparse-indexed",
                Mapping.SparseIndexed(784),
                (f, v) => NearestNeighborsQuery.SparseIndexed(f, v, Similarity.Hamming)),
        example("Jaccard LSH 1",
                "mnist-hamming-lsh-1",
                Mapping.HammingLsh(784, 100),
                (f, v) => NearestNeighborsQuery.HammingLsh(f, v, 100)),
        example("Jaccard LSH 1",
                "mnist-hamming-lsh-2",
                Mapping.HammingLsh(784, 200),
                (f, v) => NearestNeighborsQuery.HammingLsh(f, v, 100)),
      )
    )
  )
}
