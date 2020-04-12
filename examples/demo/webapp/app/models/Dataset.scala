package models

import com.klibisz.elastiknn.api._

case class Dataset(name: String, url: String, examples: Seq[Example])

case class Example(name: String, index: String, mapping: Seq[Mapping], query: Seq[NearestNeighborsQuery])

object Dataset {
  val defaults: Seq[Dataset] = Seq(
    Dataset("MNIST with Jaccard Similarity", "http://ann-benchmarks.com/mnist-784-euclidean.hdf5", Seq.empty)
  )
}