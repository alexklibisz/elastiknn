package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.ElastiknnException.ElastiknnRuntimeException
import com.klibisz.elastiknn.api.NearestNeighborsQuery._
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.models.Cache
import org.apache.lucene.search.Query
import org.elasticsearch.common.lucene.search.function.ScoreFunction
import org.elasticsearch.index.mapper.MappedFieldType
import org.elasticsearch.index.query.QueryShardContext

import scala.util._

trait ElastiknnQuery[V <: Vec] {

  def toLuceneQuery(queryShardContext: QueryShardContext): Query

  def toScoreFunction(queryShardContext: QueryShardContext): ScoreFunction

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
    (query, mapping) match {

      case (Exact(f, Similarity.Jaccard, v: Vec.SparseBool),
            _: Mapping.SparseBool | _: Mapping.SparseIndexed | _: Mapping.JaccardLsh | _: Mapping.HammingLsh) =>
        Success(new ExactQuery(f, v, ESF.Jaccard))

      case (Exact(f, Similarity.Hamming, v: Vec.SparseBool),
            _: Mapping.SparseBool | _: Mapping.SparseIndexed | _: Mapping.JaccardLsh | _: Mapping.HammingLsh) =>
        Success(new ExactQuery(f, v, ESF.Hamming))

      case (Exact(f, Similarity.L1, v: Vec.DenseFloat),
            _: Mapping.DenseFloat | _: Mapping.AngularLsh | _: Mapping.L2Lsh | _: Mapping.PermutationLsh) =>
        Success(new ExactQuery(f, v, ESF.L1))

      case (Exact(f, Similarity.L2, v: Vec.DenseFloat),
            _: Mapping.DenseFloat | _: Mapping.AngularLsh | _: Mapping.L2Lsh | _: Mapping.PermutationLsh) =>
        Success(new ExactQuery(f, v, ESF.L2))

      case (Exact(f, Similarity.Angular, v: Vec.DenseFloat),
            _: Mapping.DenseFloat | _: Mapping.AngularLsh | _: Mapping.L2Lsh | _: Mapping.PermutationLsh) =>
        Success(new ExactQuery(f, v, ESF.Angular))

      case (JaccardLsh(f, candidates, v: Vec.SparseBool, l: Float), m: Mapping.JaccardLsh) =>
        Success(new HashingQuery(f, v, candidates, l, Cache(m).hash(v.trueIndices, v.totalIndices), ESF.Jaccard))

      case (HammingLsh(f, candidates, v: Vec.SparseBool, l: Float), m: Mapping.HammingLsh) =>
        Success(new HashingQuery(f, v, candidates, l, Cache(m).hash(v.trueIndices, v.totalIndices), ESF.Hamming))

      case (AngularLsh(f, candidates, v: Vec.DenseFloat, l: Float), m: Mapping.AngularLsh) =>
        Success(new HashingQuery(f, v, candidates, l, Cache(m).hash(v.values), ESF.Angular))

      case (L2Lsh(f, candidates, probes, v: Vec.DenseFloat, l: Float), m: Mapping.L2Lsh) =>
        Success(new HashingQuery(f, v, candidates, l, Cache(m).hash(v.values, probes), ESF.L2))

      case (PermutationLsh(f, Similarity.Angular, candidates, v: Vec.DenseFloat, l: Float), m: Mapping.PermutationLsh) =>
        Success(new HashingQuery(f, v, candidates, l, Cache(m).hash(v.values), ESF.Angular))

      case (PermutationLsh(f, Similarity.L2, candidates, v: Vec.DenseFloat, l: Float), m: Mapping.PermutationLsh) =>
        Success(new HashingQuery(f, v, candidates, l, Cache(m).hash(v.values), ESF.L2))

      case (PermutationLsh(f, Similarity.L1, candidates, v: Vec.DenseFloat, l: Float), m: Mapping.PermutationLsh) =>
        Success(new HashingQuery(f, v, candidates, l, Cache(m).hash(v.values), ESF.L1))

      case _ => Failure(incompatible(query, mapping))
    }
  }
}
