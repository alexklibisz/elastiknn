package com.elastiknn.annb

import com.klibisz.elastiknn.api.Vec
import com.klibisz.elastiknn.models.{HashingModel, L2LshModel}
import io.circe.{Decoder, Json}
import org.apache.lucene.document.FieldType
import org.apache.lucene.index.{IndexOptions, IndexableField}

import java.lang
import java.util.Random

/**
  * A LuceneAlgorithm turns a vector and its ID into a set of Lucene fields.
  */
trait LuceneAlgorithm[V <: Vec.KnownDims] {

  def toDocument(id: Long, vec: V): java.lang.Iterable[IndexableField]

}

object LuceneAlgorithm {

  private val idFieldName = "id"
  private val vecFieldName = "v"

  private val idFieldType = new FieldType()
  idFieldType.setStored(true)

  private val vecFieldType = new FieldType()
  vecFieldType.setStored(false)
  vecFieldType.setOmitNorms(true)
  vecFieldType.setIndexOptions(IndexOptions.DOCS)
  vecFieldType.setTokenized(false)
  vecFieldType.setStoreTermVectors(false)

  final class ElastiknnDenseFloatHashing[D <: Dataset](dataset: Dataset, m: HashingModel.DenseFloat) extends LuceneAlgorithm[D#V] {

    override def toDocument(id: Long, vec: D#V): java.lang.Iterable[IndexableField] = {

//      val hashes = m.hash(vec.values)
//      // TODO: compare perf of ArrayList vs. LinkedList.
//      val fields = new util.ArrayList[IndexableField](hashes.length + 1)
//      fields.add(new Field(idFieldName, s"v${id}", idFieldType))
//      hashes.foreach(hf => fields.add(new Field(vecFieldName, hf.hash, vecFieldType)))
//      fields
      ???
    }

  }

//  def apply[D <: Dataset](d: Dataset, algo: Algorithm, buildArgs: Json): LuceneAlgorithm[D#V] = ???

  def apply[D <: Dataset](dataset: D, algo: Algorithm, buildArgs: Json): Either[AnnBenchmarksError, LuceneAlgorithm[D#V]] = {
    (dataset, algo, Decoder[List[Int]].decodeJson(buildArgs)) match {
      case (d @ Dataset.FashionMnist, Algorithm.ElastiknnL2Lsh, Right(List(l, k, w))) =>
//        val q = new ElastiknnDenseFloatHashing(d, new L2LshModel(dataset.dims, l, k, w, new Random(0)))
//        Right(q)
        ???
      case (_, _, Right(other))     => Left(AnnBenchmarksError.UnknownBuildArgsError(buildArgs))
      case (_, _, Left(circeError)) => Left(AnnBenchmarksError.JsonParsingError(circeError))
    }
  }

}
