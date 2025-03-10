package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.ElastiknnException.{ElastiknnIllegalArgumentException, ElastiknnRuntimeException}
import com.klibisz.elastiknn.api.NearestNeighborsQuery._
import com.klibisz.elastiknn.api.{Mapping, NearestNeighborsQuery, Similarity, Vec}
import com.klibisz.elastiknn.mapper.VectorMapper
import com.klibisz.elastiknn.models.{ModelCache, ExactSimilarityFunction => ESF}
import com.klibisz.elastiknn.vectors.FloatVectorOps
import org.elasticsearch.index.mapper.MappedFieldType
import org.elasticsearch.index.query.SearchExecutionContext

final class ElastiknnQueryBuilder(floatVectorOps: FloatVectorOps, modelCache: ModelCache) {

  private val cosine = new ESF.Cosine(floatVectorOps)
  private val dot = new ESF.Dot(floatVectorOps)
  private val l1 = new ESF.L1(floatVectorOps)
  private val l2 = new ESF.L2(floatVectorOps)

  private def incompatible(q: NearestNeighborsQuery, m: Mapping): Exception =
    new ElastiknnIllegalArgumentException(s"Query [$q] is not compatible with mapping [$m]")

  private def getMapping(context: SearchExecutionContext, field: String): Mapping = {
    import VectorMapper._
    val mft: MappedFieldType = context.getFieldType(field)
    mft match {
      case ft: FieldType => ft.mapping
      case null =>
        throw new ElastiknnRuntimeException(s"Could not find mapped field type for field [${field}]")
      case _ =>
        throw new ElastiknnRuntimeException(
          s"Expected field [${mft.name}] to have type [${DenseFloatVectorMapper.CONTENT_TYPE}] or [${SparseBoolVectorMapper.CONTENT_TYPE}] but had [${mft.typeName}]"
        )
    }
  }

  def build(query: NearestNeighborsQuery, queryShardContext: SearchExecutionContext): ElastiknnQuery =
    build(query, getMapping(queryShardContext, query.field))

  def build(query: NearestNeighborsQuery, mapping: Mapping): ElastiknnQuery =
    (query, mapping) match {
      case (
            Exact(f, Similarity.Jaccard, v: Vec.SparseBool),
            _: Mapping.SparseBool | _: Mapping.JaccardLsh | _: Mapping.HammingLsh
          ) =>
        new ExactQuery(f, v, ESF.Jaccard)

      case (
            Exact(f, Similarity.Hamming, v: Vec.SparseBool),
            _: Mapping.SparseBool | _: Mapping.JaccardLsh | _: Mapping.HammingLsh
          ) =>
        new ExactQuery(f, v, ESF.Hamming)

      case (
            Exact(f, Similarity.L1, v: Vec.DenseFloat),
            _: Mapping.DenseFloat | _: Mapping.CosineLsh |  _: Mapping.DotLsh |_: Mapping.L2Lsh | _: Mapping.PermutationLsh
          ) =>
        new ExactQuery(f, v, l1)

      case (
            Exact(f, Similarity.L2, v: Vec.DenseFloat),
            _: Mapping.DenseFloat | _: Mapping.CosineLsh |  _: Mapping.DotLsh | _: Mapping.L2Lsh | _: Mapping.PermutationLsh
          ) =>
        new ExactQuery(f, v, l2)

      case (
            Exact(f, Similarity.Cosine, v: Vec.DenseFloat),
            _: Mapping.DenseFloat | _: Mapping.CosineLsh |  _: Mapping.DotLsh | _: Mapping.L2Lsh | _: Mapping.PermutationLsh
          ) =>
        new ExactQuery(f, v, cosine)

      case (
            Exact(f, Similarity.Dot, v: Vec.DenseFloat),
            _: Mapping.DenseFloat | _: Mapping.CosineLsh |  _: Mapping.DotLsh | _: Mapping.L2Lsh | _: Mapping.PermutationLsh
          ) =>
        new ExactQuery(f, v, dot)

      case (JaccardLsh(f, candidates, v: Vec.SparseBool), m: Mapping.JaccardLsh) =>
        new HashingQuery(f, v, candidates, modelCache(m).hash(v.trueIndices, v.totalIndices), ESF.Jaccard)

      case (HammingLsh(f, candidates, v: Vec.SparseBool), m: Mapping.HammingLsh) =>
        new HashingQuery(f, v, candidates, modelCache(m).hash(v.trueIndices, v.totalIndices), ESF.Hamming)

      case (CosineLsh(f, candidates, v: Vec.DenseFloat), m: Mapping.CosineLsh) =>
        new HashingQuery(f, v, candidates, modelCache(m).hash(v.values), cosine)
      
      case (DotLsh(f, candidates, v: Vec.DenseFloat), m: Mapping.DotLsh) =>
        new HashingQuery(f, v, candidates, modelCache(m).hash(v.values), dot)
      
      case (L2Lsh(f, candidates, probes, v: Vec.DenseFloat), m: Mapping.L2Lsh) =>
        new HashingQuery(f, v, candidates, modelCache(m).hash(v.values, probes), l2)

      case (PermutationLsh(f, Similarity.Cosine, candidates, v: Vec.DenseFloat), m: Mapping.PermutationLsh) =>
        new HashingQuery(f, v, candidates, modelCache(m).hash(v.values), cosine)

      case (PermutationLsh(f, Similarity.L2, candidates, v: Vec.DenseFloat), m: Mapping.PermutationLsh) =>
        new HashingQuery(f, v, candidates, modelCache(m).hash(v.values), l2)

      case (PermutationLsh(f, Similarity.L1, candidates, v: Vec.DenseFloat), m: Mapping.PermutationLsh) =>
        new HashingQuery(f, v, candidates, modelCache(m).hash(v.values), l1)

      case _ => throw incompatible(query, mapping)
    }
}
