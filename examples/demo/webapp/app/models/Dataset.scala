package models

import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.client.ElastiknnRequests
import com.sksamuel.elastic4s.Hit
import com.sksamuel.elastic4s.requests.indexes.PutMappingBuilderFn
import com.sksamuel.elastic4s.requests.mappings.PutMappingRequest
import io.circe.generic.semiauto._
import io.circe.{Encoder, Json}

import scala.util.Try

case class Dataset(prettyName: String, sourceName: String, permalink: String, sourceLink: String, examples: Seq[Example]) {
  def parseHit(hit: Hit): Try[SearchResult] =
    if (sourceName.startsWith("mnist") || sourceName.startsWith("cifar")) SearchResult.Image.parseHit(hit)
    else SearchResult.WordVector.parseHit(hit)
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
      "https://keras.io/datasets/",
      Seq(
        example("Exact", "mnist-jaccard-exact", Mapping.SparseBool(784), (f, v) => NearestNeighborsQuery.Exact(f, v, Similarity.Jaccard)),
        example("Sparse Indexed",
                "mnist-jaccard-sparse-indexed",
                Mapping.SparseIndexed(784),
                (f, v) => NearestNeighborsQuery.SparseIndexed(f, v, Similarity.Jaccard)),
        example("Jaccard LSH #1",
                "mnist-jaccard-lsh-1",
                Mapping.JaccardLsh(784, 100, 1),
                (f, v) => NearestNeighborsQuery.JaccardLsh(f, 100, v)),
        example("Jaccard LSH #2",
                "mnist-jaccard-lsh-2",
                Mapping.JaccardLsh(784, 100, 1),
                (f, v) => NearestNeighborsQuery.JaccardLsh(f, 20, v)),
      )
    ),
    Dataset(
      "MNIST Digits with Hamming Similarity",
      "mnist_binary",
      "mnist-hamming",
      "https://keras.io/datasets/",
      Seq(
        example("Exact",
                "mnist-hamming-exact",
                Mapping.SparseBool(784),
                (field, vec) => NearestNeighborsQuery.Exact(field, vec, Similarity.Hamming)),
        example("Sparse Indexed",
                "mnist-hamming-sparse-indexed",
                Mapping.SparseIndexed(784),
                (f, v) => NearestNeighborsQuery.SparseIndexed(f, v, Similarity.Hamming)),
        example("Hamming LSH #1",
                "mnist-hamming-lsh-1",
                Mapping.HammingLsh(784, 100),
                (f, v) => NearestNeighborsQuery.HammingLsh(f, 100, v)),
        example("Hamming LSH #2",
                "mnist-hamming-lsh-2",
                Mapping.HammingLsh(784, 100),
                (f, v) => NearestNeighborsQuery.HammingLsh(f, 20, v)),
      )
    ),
    Dataset(
      "Google News 2013 Word Vectors with Angular Similarity",
      "word2vec-google-300",
      "word2vec-google-angular",
      "http://vectors.nlpl.eu/repository",
      Seq(
        example(
          "Exact",
          "word2vec-google-angular-exact",
          Mapping.DenseFloat(300),
          (f, v) => NearestNeighborsQuery.Exact(f, v, Similarity.Angular)
        ),
        example("Angular LSH 1",
                "word2vec-google-angular-lsh-1",
                Mapping.AngularLsh(300, 100, 1),
                (f, v) => NearestNeighborsQuery.AngularLsh(f, 100, v)),
        example("Angular LSH 2",
                "word2vec-google-angular-lsh-2",
                Mapping.AngularLsh(300, 100, 1),
                (f, v) => NearestNeighborsQuery.AngularLsh(f, 20, v)),
      )
    ),
    Dataset(
      "CIFAR with L2 Similarity",
      "cifar",
      "cifar-l2",
      "https://keras.io/datasets/",
      Seq(
        example("Exact", "cifar-l2-exact", Mapping.DenseFloat(3072), (f, v) => NearestNeighborsQuery.Exact(f, v, Similarity.L2)),
        example("L2 LSH #1", "cifar-l2-lsh-1", Mapping.L2Lsh(3072, 100, 1, 3), (f, v) => NearestNeighborsQuery.L2Lsh(f, 100, v)),
        example("L2 LSH #2", "cifar-l2-lsh-2", Mapping.L2Lsh(3072, 100, 1, 3), (f, v) => NearestNeighborsQuery.L2Lsh(f, 20, v)),
      )
    )
  )
}
