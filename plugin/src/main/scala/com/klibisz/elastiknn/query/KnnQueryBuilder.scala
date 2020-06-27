package com.klibisz.elastiknn.query

import java.time.Duration
import java.util.Objects

import com.google.common.cache.{Cache, CacheBuilder}
import com.google.common.io.BaseEncoding
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.models.{ExactSimilarityFunction, SparseIndexedSimilarityFunction}
import com.klibisz.elastiknn.storage.StoredVec
import com.klibisz.elastiknn.utils.CirceUtils.javaMapEncoder
import com.klibisz.elastiknn.{ELASTIKNN_NAME, api}
import io.circe.Json
import org.apache.lucene.search.{Query, LshQuery => LuceneLshQuery}
import org.apache.lucene.util.SetOnce
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.mapping.get._
import org.elasticsearch.action.get.{GetAction, GetRequest, GetResponse}
import org.elasticsearch.client.Client
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query._

object KnnQueryBuilder {

  val NAME: String = s"${ELASTIKNN_NAME}_nearest_neighbors"

  private val b64 = BaseEncoding.base64()
  private def encodeB64[T: ElasticsearchCodec](t: T): String = b64.encode(encode(t).noSpaces.getBytes)
  private def decodeB64[T: ElasticsearchCodec](s: String): T = parse(new String(b64.decode(s))).flatMap(decodeJson[T]).toTry.get

  object Parser extends QueryParser[KnnQueryBuilder] {
    override def fromXContent(parser: XContentParser): KnnQueryBuilder = {
      val json: Json = javaMapEncoder(parser.map)
      val queryTry = ElasticsearchCodec.decodeJson[NearestNeighborsQuery](json).toTry
      new KnnQueryBuilder(queryTry.get)
    }
  }

  object Reader extends Writeable.Reader[KnnQueryBuilder] {
    override def read(in: StreamInput): KnnQueryBuilder = {
      in.readFloat() // boost
      in.readOptionalString() // query name
      new KnnQueryBuilder(decodeB64[NearestNeighborsQuery](in.readString()))
    }
  }

  private val mappingCache: Cache[(String, String), Mapping] =
    CacheBuilder.newBuilder.expireAfterWrite(Duration.ofMinutes(1)).build()

}

final case class KnnQueryBuilder(query: NearestNeighborsQuery) extends AbstractQueryBuilder[KnnQueryBuilder] {

  override def doWriteTo(out: StreamOutput): Unit = out.writeString(KnnQueryBuilder.encodeB64(query))

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doRewrite(context: QueryRewriteContext): QueryBuilder = query.vec match {
    case ixv: Vec.Indexed => rewriteGetVector(context, ixv)
    case _                => this
  }

  override def doToQuery(c: QueryShardContext): Query = {
    // Have to get the mapping inside doToQuery because only QueryShardContext defines the index name and a client to make requests.
    val mapping: Mapping = getMapping(c)
    import NearestNeighborsQuery._

    (query, mapping) match {
      case (Exact(f, Similarity.Jaccard, v: Vec.SparseBool),
            _: Mapping.SparseBool | _: Mapping.SparseIndexed | _: Mapping.JaccardLsh | _: Mapping.HammingLsh) =>
        ExactQuery(f, v, ExactSimilarityFunction.Jaccard)

      case (Exact(f, Similarity.Hamming, v: Vec.SparseBool),
            _: Mapping.SparseBool | _: Mapping.SparseIndexed | _: Mapping.JaccardLsh | _: Mapping.HammingLsh) =>
        ExactQuery(f, v, ExactSimilarityFunction.Hamming)

      case (Exact(f, Similarity.L1, v: Vec.DenseFloat), _: Mapping.DenseFloat | _: Mapping.AngularLsh | _: Mapping.L2Lsh) =>
        ExactQuery(f, v, ExactSimilarityFunction.L1)

      case (Exact(f, Similarity.L2, v: Vec.DenseFloat), _: Mapping.DenseFloat | _: Mapping.AngularLsh | _: Mapping.L2Lsh) =>
        ExactQuery(f, v, ExactSimilarityFunction.L2)

      case (Exact(f, Similarity.Angular, v: Vec.DenseFloat), _: Mapping.DenseFloat | _: Mapping.AngularLsh | _: Mapping.L2Lsh) =>
        ExactQuery(f, v, ExactSimilarityFunction.Angular)

      case (SparseIndexed(f, Similarity.Jaccard, sbv: Vec.SparseBool), _: Mapping.SparseIndexed) =>
        SparseIndexedQuery(f, sbv, SparseIndexedSimilarityFunction.Jaccard)

      case (SparseIndexed(f, Similarity.Hamming, sbv: Vec.SparseBool), _: Mapping.SparseIndexed) =>
        SparseIndexedQuery(f, sbv, SparseIndexedSimilarityFunction.Hamming)

      case (JaccardLsh(f, candidates, v: Vec.SparseBool, _), m: Mapping.JaccardLsh) =>
        LuceneLshQuery(f, v, candidates, LshFunctionCache.Jaccard(m), c.getIndexReader)

      case (HammingLsh(f, candidates, v: Vec.SparseBool, _), m: Mapping.HammingLsh) =>
        LuceneLshQuery(f, v, candidates, LshFunctionCache.Hamming(m), c.getIndexReader)

      case (AngularLsh(f, candidates, v: Vec.DenseFloat, _), m: Mapping.AngularLsh) =>
        LuceneLshQuery(f, v, candidates, LshFunctionCache.Angular(m), c.getIndexReader)

      case (L2Lsh(f, candidates, v: Vec.DenseFloat, _), m: Mapping.L2Lsh) =>
        LuceneLshQuery(f, v, candidates, LshFunctionCache.L2(m), c.getIndexReader)

      case _ => throw incompatible(mapping, query)
    }
  }

  private def incompatible(m: Mapping, q: NearestNeighborsQuery): Exception = {
    val msg = s"Query [${ElasticsearchCodec.encode(q).noSpaces}] is not compatible with mapping [${ElasticsearchCodec.encode(m).noSpaces}]"
    new IllegalArgumentException(msg)
  }

  private def getMapping(context: QueryShardContext): Mapping = {
    import KnnQueryBuilder.mappingCache
    val index = context.index.getName
    mappingCache.get(
      (index, query.field),
      () =>
        try {
          val client = context.getClient
          val request = new GetFieldMappingsRequest().indices(index).fields(query.field)
          val response = client.execute(GetFieldMappingsAction.INSTANCE, request).actionGet(1000)
          val srcMap = response
            .mappings()
            .get(index)
            .get("_doc")
            .get(query.field)
            .sourceAsMap()
            .get(query.field)
            .asInstanceOf[JavaJsonMap]
          val srcJson = javaMapEncoder(srcMap)
          val mapping = ElasticsearchCodec.decodeJsonGet[Mapping](srcJson)
          mapping
        } catch {
          case e: Exception => throw new RuntimeException(s"Failed to retrieve mapping at index [$index] field [${query.field}]", e)
      }
    )
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
