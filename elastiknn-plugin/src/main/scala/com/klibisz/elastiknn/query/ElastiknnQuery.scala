package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.ElastiknnException.ElastiknnRuntimeException
import com.klibisz.elastiknn.api.NearestNeighborsQuery._
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.models.{Cache, SparseIndexedSimilarityFunction}
import org.apache.lucene.index.IndexReader
import org.apache.lucene.search.Query
import org.elasticsearch.common.lucene.search.function.ScoreFunction
import org.elasticsearch.index.mapper.MappedFieldType
import org.elasticsearch.index.query.QueryShardContext

import scala.language.implicitConversions
import scala.util._

/**
  * Useful way to represent a query. The name is meh.
  */
trait ElastiknnQuery[V <: Vec] {
  def toLuceneQuery(indexReader: IndexReader): Query
  def toScoreFunction(indexReader: IndexReader): ScoreFunction
}

object ElastiknnQuery {

  private def incompatible(q: NearestNeighborsQuery, m: Mapping): Exception = {
    val msg = s"Query [${ElasticsearchCodec.encode(q).noSpaces}] is not compatible with mapping [${ElasticsearchCodec.encode(m).noSpaces}]"
    new IllegalArgumentException(msg)
  }

  def getMapping(context: QueryShardContext, field: String): Mapping = {
    import VectorMapper._
    val mft: MappedFieldType = context.fieldMapper(field)
    mft match {
      case ft: FieldType => ft.mapping
      case null =>
        throw new ElastiknnRuntimeException(s"Could not find mapped field type for field [${field}]")
      case _ =>
        throw new ElastiknnRuntimeException(
          s"Expected field [${mft.name}] to have type [${denseFloatVector.CONTENT_TYPE}] or [${sparseBoolVector.CONTENT_TYPE}] but had [${mft.typeName}]")
    }
  }

  def apply(query: NearestNeighborsQuery, queryShardContext: QueryShardContext): Try[ElastiknnQuery[_]] =
    apply(query, getMapping(queryShardContext, query.field))

  def apply(query: NearestNeighborsQuery, mapping: Mapping): Try[ElastiknnQuery[_]] = {
    import com.klibisz.elastiknn.models.{ExactSimilarityFunction => ESF}
    implicit def toSuccess[A <: Vec](q: ElastiknnQuery[A]): Try[ElastiknnQuery[A]] = Success(q)
    (query, mapping) match {

      case (Exact(f, Similarity.Jaccard, v: Vec.SparseBool),
            _: Mapping.SparseBool | _: Mapping.SparseIndexed | _: Mapping.JaccardLsh | _: Mapping.HammingLsh) =>
        new ExactQuery(f, v, ESF.Jaccard)

      case (Exact(f, Similarity.Hamming, v: Vec.SparseBool),
            _: Mapping.SparseBool | _: Mapping.SparseIndexed | _: Mapping.JaccardLsh | _: Mapping.HammingLsh) =>
        new ExactQuery(f, v, ESF.Hamming)

      case (Exact(f, Similarity.L1, v: Vec.DenseFloat),
            _: Mapping.DenseFloat | _: Mapping.AngularLsh | _: Mapping.L2Lsh | _: Mapping.PermutationLsh) =>
        new ExactQuery(f, v, ESF.L1)

      case (Exact(f, Similarity.L2, v: Vec.DenseFloat),
            _: Mapping.DenseFloat | _: Mapping.AngularLsh | _: Mapping.L2Lsh | _: Mapping.PermutationLsh) =>
        new ExactQuery(f, v, ESF.L2)

      case (Exact(f, Similarity.Angular, v: Vec.DenseFloat),
            _: Mapping.DenseFloat | _: Mapping.AngularLsh | _: Mapping.L2Lsh | _: Mapping.PermutationLsh) =>
        new ExactQuery(f, v, ESF.Angular)

      case (SparseIndexed(f, Similarity.Jaccard, sbv: Vec.SparseBool), _: Mapping.SparseIndexed) =>
        new SparseIndexedQuery(f, sbv, SparseIndexedSimilarityFunction.Jaccard)

      case (SparseIndexed(f, Similarity.Hamming, sbv: Vec.SparseBool), _: Mapping.SparseIndexed) =>
        new SparseIndexedQuery(f, sbv, SparseIndexedSimilarityFunction.Hamming)

      case (JaccardLsh(f, candidates, v: Vec.SparseBool, l: Float), m: Mapping.JaccardLsh) =>
        new HashingQuery(f, v, candidates, l, Cache(m).hash(v.trueIndices, v.totalIndices), ESF.Jaccard)

      case (HammingLsh(f, candidates, v: Vec.SparseBool, l: Float), m: Mapping.HammingLsh) =>
        new HashingQuery(f, v, candidates, l, Cache(m).hash(v.trueIndices, v.totalIndices), ESF.Hamming)

      case (AngularLsh(f, candidates, v: Vec.DenseFloat, l: Float), m: Mapping.AngularLsh) =>
        new HashingQuery(f, v, candidates, l, Cache(m).hash(v.values), ESF.Angular)

      case (L2Lsh(f, candidates, probes, v: Vec.DenseFloat, l: Float), m: Mapping.L2Lsh) =>
        new HashingQuery(f, v, candidates, l, Cache(m).hash(v.values, probes), ESF.L2)

      case (PermutationLsh(f, Similarity.Angular, candidates, v: Vec.DenseFloat, l: Float), m: Mapping.PermutationLsh) =>
        new HashingQuery(f, v, candidates, l, Cache(m).hash(v.values), ESF.Angular)

      case (PermutationLsh(f, Similarity.L2, candidates, v: Vec.DenseFloat, l: Float), m: Mapping.PermutationLsh) =>
        new HashingQuery(f, v, candidates, l, Cache(m).hash(v.values), ESF.L2)

      case (PermutationLsh(f, Similarity.L1, candidates, v: Vec.DenseFloat, l: Float), m: Mapping.PermutationLsh) =>
        new HashingQuery(f, v, candidates, l, Cache(m).hash(v.values), ESF.L1)

      case _ => Failure(incompatible(query, mapping))
    }
  }
}
