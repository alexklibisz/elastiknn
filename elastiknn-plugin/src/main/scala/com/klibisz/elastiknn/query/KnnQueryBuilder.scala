package com.klibisz.elastiknn.query

import java.time.Duration
import java.util.Objects

import com.google.common.cache.{Cache, CacheBuilder}
import com.google.common.io.BaseEncoding
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.models.SparseIndexedSimilarityFunction
import com.klibisz.elastiknn.{ELASTIKNN_NAME, api}
import io.circe.Json
import org.apache.lucene.index.IndexReader
import org.apache.lucene.search.Query
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
      val map = parser.map()
      val json: Json = ??? // javaMapEncoder(map)
      val query = ElasticsearchCodec.decodeJsonGet[NearestNeighborsQuery](json)
      new KnnQueryBuilder(query)
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

  private val mappingCache: Cache[(String, String), Mapping] =
    CacheBuilder.newBuilder.expireAfterWrite(Duration.ofMinutes(1)).build()

  def router(query: NearestNeighborsQuery, mapping: Mapping, indexReader: IndexReader): Query = {
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

      case (JaccardLsh(f, candidates, v: Vec.SparseBool), m: Mapping.JaccardLsh) =>
        HashingQuery(f, v, candidates, HashingFunctionCache.Jaccard(m), ESF.Jaccard, indexReader)

      case (HammingLsh(f, candidates, v: Vec.SparseBool), m: Mapping.HammingLsh) =>
        HashingQuery(f, v, candidates, HashingFunctionCache.Hamming(m), ESF.Hamming, indexReader)

      case (AngularLsh(f, candidates, v: Vec.DenseFloat), m: Mapping.AngularLsh) =>
        HashingQuery(f, v, candidates, HashingFunctionCache.Angular(m), ESF.Angular, indexReader)

      case (L2Lsh(f, candidates, probes, v: Vec.DenseFloat), m: Mapping.L2Lsh) =>
        HashingQuery(f, v, candidates, HashingFunctionCache.L2(m).hashWithProbes(v, probes), ESF.L2, indexReader)

      case (PermutationLsh(f, Similarity.Angular, candidates, v: Vec.DenseFloat), m: Mapping.PermutationLsh) =>
        HashingQuery(f, v, candidates, HashingFunctionCache.Permutation(m), ESF.Angular, indexReader)

      case (PermutationLsh(f, Similarity.L2, candidates, v: Vec.DenseFloat), m: Mapping.PermutationLsh) =>
        HashingQuery(f, v, candidates, HashingFunctionCache.Permutation(m), ESF.L2, indexReader)

      case (PermutationLsh(f, Similarity.L1, candidates, v: Vec.DenseFloat), m: Mapping.PermutationLsh) =>
        HashingQuery(f, v, candidates, HashingFunctionCache.Permutation(m), ESF.L1, indexReader)

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

  override def doToQuery(context: QueryShardContext): Query = KnnQueryBuilder.router(query, getMapping(context), context.getIndexReader)

  private def getMapping(context: QueryShardContext): Mapping = {
    import KnnQueryBuilder.mappingCache
    val index = context.index.getName
    try {
      mappingCache.get(
        (index, query.field),
        () => {
          val client = context.getClient
          val request = new GetFieldMappingsRequest().indices(index).fields(query.field)
          val response = client.execute(GetFieldMappingsAction.INSTANCE, request).actionGet(1000)
          val mappingMap = response
            .mappings()
            .get(index)
            .get("_doc")
            .get(query.field)
            .sourceAsMap()
            .get(query.field.split('.').last) // For nested fields e.g. "foo.bar.vec" -> "vec"
          val mappingJsonMap = mappingMap.asInstanceOf[JavaJsonMap]
          val mappingJson: Json = ??? // javaMapEncoder(mappingJsonMap)
          ElasticsearchCodec.decodeJsonGet[Mapping](mappingJson)
        }
      )
    } catch {
      // Guava cache wraps any exceptions in an ExecutionException. Pass along only the cause.
      case e: java.util.concurrent.ExecutionException =>
        throw new RuntimeException(s"Failed to retrieve mapping at index [$index] field [${query.field}]", e.getCause)
      case e: Throwable => throw e
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
              val srcJson: Json = ??? // javaMapEncoder(srcMap)
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
