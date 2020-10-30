package com.klibisz.elastiknn.query

import java.util.Objects

import com.google.common.io.BaseEncoding
import com.klibisz.elastiknn.ElastiknnException.ElastiknnRuntimeException
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.models.{SparseIndexedSimilarityFunction, Cache => ModelCache}
import com.klibisz.elastiknn.utils.CirceUtils.javaMapEncoder
import com.klibisz.elastiknn.{ELASTIKNN_NAME, api}
import com.klibisz.elastiknn.mapper.VectorMapper
import io.circe.Json
import org.apache.lucene.index.IndexReader
import org.apache.lucene.search.Query
import org.apache.lucene.util.SetOnce
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.get.{GetAction, GetRequest, GetResponse}
import org.elasticsearch.client.Client
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.mapper.MappedFieldType
import org.elasticsearch.index.query._

object KnnQueryBuilder {

  val NAME: String = s"${ELASTIKNN_NAME}_nearest_neighbors"

  private val b64 = BaseEncoding.base64()
  private def encodeB64[T: ElasticsearchCodec](t: T): String = b64.encode(encode(t).noSpaces.getBytes)
  private def decodeB64[T: ElasticsearchCodec](s: String): T = parse(new String(b64.decode(s))).flatMap(decodeJson[T]).toTry.get

  object Parser extends QueryParser[KnnQueryBuilder] {
    override def fromXContent(parser: XContentParser): KnnQueryBuilder = {
      val map = parser.map()
      val json: Json = javaMapEncoder(map)
      val query = ElasticsearchCodec.decodeJsonGet[NearestNeighborsQuery](json)
      // Account for sparse bool vecs which need to be sorted.
      val sortedVec = query.vec match {
        case v: Vec.SparseBool if !v.isSorted => v.sorted()
        case _                                => query.vec
      }
      new KnnQueryBuilder(query.withVec(sortedVec))
    }
  }

  object Reader extends Writeable.Reader[KnnQueryBuilder] {
    override def read(in: StreamInput): KnnQueryBuilder = {
      in.readFloat() // boost
      in.readOptionalString() // query name
      val query = decodeB64[NearestNeighborsQuery](in.readString())
      new KnnQueryBuilder(query)
    }
  }

  def apply(query: NearestNeighborsQuery, mapping: Mapping, indexReader: IndexReader): Query = {
    import NearestNeighborsQuery._
    import com.klibisz.elastiknn.models.{ExactSimilarityFunction => ESF}

    (query, mapping) match {

      case (Exact(f, Similarity.Jaccard, v: Vec.SparseBool),
            _: Mapping.SparseBool | _: Mapping.SparseIndexed | _: Mapping.JaccardLsh | _: Mapping.HammingLsh) =>
        ExactQuery(f, v, ESF.Jaccard)

      case (Exact(f, Similarity.Hamming, v: Vec.SparseBool),
            _: Mapping.SparseBool | _: Mapping.SparseIndexed | _: Mapping.JaccardLsh | _: Mapping.HammingLsh) =>
        ExactQuery(f, v, ESF.Hamming)

      case (Exact(f, Similarity.L1, v: Vec.DenseFloat),
            _: Mapping.DenseFloat | _: Mapping.AngularLsh | _: Mapping.L2Lsh | _: Mapping.PermutationLsh) =>
        ExactQuery(f, v, ESF.L1)

      case (Exact(f, Similarity.L2, v: Vec.DenseFloat),
            _: Mapping.DenseFloat | _: Mapping.AngularLsh | _: Mapping.L2Lsh | _: Mapping.PermutationLsh) =>
        ExactQuery(f, v, ESF.L2)

      case (Exact(f, Similarity.Angular, v: Vec.DenseFloat),
            _: Mapping.DenseFloat | _: Mapping.AngularLsh | _: Mapping.L2Lsh | _: Mapping.PermutationLsh) =>
        ExactQuery(f, v, ESF.Angular)

      case (SparseIndexed(f, Similarity.Jaccard, sbv: Vec.SparseBool), _: Mapping.SparseIndexed) =>
        SparseIndexedQuery(f, sbv, SparseIndexedSimilarityFunction.Jaccard, indexReader)

      case (SparseIndexed(f, Similarity.Hamming, sbv: Vec.SparseBool), _: Mapping.SparseIndexed) =>
        SparseIndexedQuery(f, sbv, SparseIndexedSimilarityFunction.Hamming, indexReader)

      case (JaccardLsh(f, candidates, v: Vec.SparseBool, l: Float), m: Mapping.JaccardLsh) =>
        HashingQuery(f, v, candidates, l, ModelCache(m).hash(v.trueIndices, v.totalIndices), ESF.Jaccard, indexReader)

      case (HammingLsh(f, candidates, v: Vec.SparseBool, l: Float), m: Mapping.HammingLsh) =>
        HashingQuery(f, v, candidates, l, ModelCache(m).hash(v.trueIndices, v.totalIndices), ESF.Hamming, indexReader)

      case (AngularLsh(f, candidates, v: Vec.DenseFloat, l: Float), m: Mapping.AngularLsh) =>
        HashingQuery(f, v, candidates, l, ModelCache(m).hash(v.values), ESF.Angular, indexReader)

      case (L2Lsh(f, candidates, probes, v: Vec.DenseFloat, l: Float), m: Mapping.L2Lsh) =>
        HashingQuery(f, v, candidates, l, ModelCache(m).hash(v.values, probes), ESF.L2, indexReader)

      case (PermutationLsh(f, Similarity.Angular, candidates, v: Vec.DenseFloat, l: Float), m: Mapping.PermutationLsh) =>
        HashingQuery(f, v, candidates, l, ModelCache(m).hash(v.values), ESF.Angular, indexReader)

      case (PermutationLsh(f, Similarity.L2, candidates, v: Vec.DenseFloat, l: Float), m: Mapping.PermutationLsh) =>
        HashingQuery(f, v, candidates, l, ModelCache(m).hash(v.values), ESF.L2, indexReader)

      case (PermutationLsh(f, Similarity.L1, candidates, v: Vec.DenseFloat, l: Float), m: Mapping.PermutationLsh) =>
        HashingQuery(f, v, candidates, l, ModelCache(m).hash(v.values), ESF.L1, indexReader)

      case _ => throw incompatible(mapping, query)
    }
  }

  private def incompatible(m: Mapping, q: NearestNeighborsQuery): Exception = {
    val msg = s"Query [${ElasticsearchCodec.encode(q).noSpaces}] is not compatible with mapping [${ElasticsearchCodec.encode(m).noSpaces}]"
    new IllegalArgumentException(msg)
  }

}

final case class KnnQueryBuilder(query: NearestNeighborsQuery) extends AbstractQueryBuilder[KnnQueryBuilder] {

  override def doWriteTo(out: StreamOutput): Unit = {
    out.writeString(KnnQueryBuilder.encodeB64(query))
  }

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doRewrite(context: QueryRewriteContext): QueryBuilder = query.vec match {
    case ixv: Vec.Indexed => rewriteGetVector(context, ixv)
    case _                => this
  }

  override def doToQuery(context: QueryShardContext): Query = KnnQueryBuilder(query, getMapping(context), context.getIndexReader)

  private def getMapping(context: QueryShardContext): Mapping = {
    import VectorMapper._
    val mft: MappedFieldType = context.fieldMapper(query.field)
    mft match {
      case ft: FieldType => ft.mapping
      case null =>
        throw new ElastiknnRuntimeException(s"Could not find mapped field type for field [${query.field}]")
      case _ =>
        throw new ElastiknnRuntimeException(
          s"Expected field [${mft.name}] to have type [${denseFloatVector.CONTENT_TYPE}] or [${sparseBoolVector.CONTENT_TYPE}] but had [${mft.typeName}]")
    }
  }

  override def doEquals(other: KnnQueryBuilder): Boolean = other.query == this.query

  override def doHashCode(): Int = Objects.hash(query)

  override def getWriteableName: String = KnnQueryBuilder.NAME

  private def rewriteGetVector(c: QueryRewriteContext, ixv: api.Vec.Indexed): QueryBuilder = {
    def ex(e: Exception) = new RuntimeException(s"Failed to retrieve vector at index [${ixv.index}] id [${ixv.id}] field [${ixv.field}]", e)
    val supplier = new SetOnce[KnnQueryBuilder]()
    c.registerAsyncAction((client: Client, l: ActionListener[_]) => {
      client.execute(
        GetAction.INSTANCE,
        new GetRequest(ixv.index, ixv.id),
        new ActionListener[GetResponse] {
          override def onResponse(response: GetResponse): Unit =
            try {
              val srcMap = response.getSourceAsMap.get(ixv.field).asInstanceOf[JavaJsonMap]
              val srcJson: Json = javaMapEncoder(srcMap)
              val vector = ElasticsearchCodec.decodeJsonGet[api.Vec](srcJson)
              supplier.set(copy(query.withVec(vector)))
              l.asInstanceOf[ActionListener[Any]].onResponse(null)
            } catch {
              case e: Exception => l.onFailure(ex(e))
            }
          override def onFailure(e: Exception): Unit = l.onFailure(ex(e))
        }
      )
    })
    RewriteQueryBuilder(_ => supplier.get())
  }
}
