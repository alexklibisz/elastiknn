package com.klibisz.elastiknn.query

import java.time.Duration
import java.util.Objects

import com.google.common.cache.{Cache, CacheBuilder}
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import com.klibisz.elastiknn.api.Query.NearestNeighborsQuery
import com.klibisz.elastiknn.api.{ElasticsearchCodec, Mapping, SparseBoolVectorModelOptions}
import com.klibisz.elastiknn.utils.CirceUtils.javaMapEncoder
import com.klibisz.elastiknn.{ELASTIKNN_NAME, api}
import io.circe.Json
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

  object Parser extends QueryParser[KnnQueryBuilder] {
    override def fromXContent(parser: XContentParser): KnnQueryBuilder = {
      val json: Json = javaMapEncoder(parser.map)
      val queryTry = ElasticsearchCodec.decode[NearestNeighborsQuery](json).toTry
      new KnnQueryBuilder(queryTry.get)
    }
  }

  object Reader extends Writeable.Reader[KnnQueryBuilder] {
    override def read(in: StreamInput): KnnQueryBuilder = {
      // https://github.com/elastic/elasticsearch/blob/master/server/src/main/java/org/elasticsearch/index/query/AbstractQueryBuilder.java#L66-L68
      in.readFloat()
      in.readOptionalString()
      new KnnQueryBuilder(ElasticsearchCodec.parseGetB64[NearestNeighborsQuery](in.readString()))
    }
  }

  private val mappingCache: Cache[(String, String), Mapping] = CacheBuilder.newBuilder.expireAfterWrite(Duration.ofMinutes(1)).build()

}

final case class KnnQueryBuilder(query: NearestNeighborsQuery, mappingOpt: Option[Mapping] = None)
    extends AbstractQueryBuilder[KnnQueryBuilder] {
  override def doWriteTo(out: StreamOutput): Unit = out.writeString(ElasticsearchCodec.encodeB64(query))

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doToQuery(context: QueryShardContext): Query = (mappingOpt, query.vector) match {
    case (Some(m: Mapping.SparseBoolVector), v: api.Vector.SparseBoolVector) => sparseBool(context, m, v)
    case (Some(m: Mapping.DenseFloatVector), v: api.Vector.DenseFloatVector) => denseFloat(context, m, v)
    case (Some(m: Mapping), v: api.Vector)                                   => throw new IllegalArgumentException(s"Mapping $m is not compatible with vector $v")
    case (None, _)                                                           => throw new IllegalArgumentException(s"Mapping is missing.")
  }

  override def doEquals(other: KnnQueryBuilder): Boolean = other.query == this.query

  override def doHashCode(): Int = Objects.hash(query, mappingOpt)

  override def getWriteableName: String = KnnQueryBuilder.NAME

  override def doRewrite(context: QueryRewriteContext): QueryBuilder = (mappingOpt, query.vector) match {
    case (None, _)                          => rewriteGetMapping(context)
    case (_, ixv: api.Vector.IndexedVector) => rewriteGetVector(context, ixv)
    case _                                  => this
  }

  // Fetch the mapping and return a [[KnnQueryBuilder]] with the mapping defined.
  private def rewriteGetMapping(c: QueryRewriteContext): QueryBuilder = {
    import KnnQueryBuilder.mappingCache
    def ex(e: Exception): Exception =
      new RuntimeException(s"Failed to retrieve mapping at index [${query.index}] field [${query.field}]", e)
    val maybeCached: Mapping = mappingCache.getIfPresent((query.index, query.field))
    if (maybeCached != null) copy(mappingOpt = Some(maybeCached))
    else {
      val supplier = new SetOnce[KnnQueryBuilder]()
      c.registerAsyncAction((client: Client, l: ActionListener[_]) => {
        client.execute(
          GetFieldMappingsAction.INSTANCE,
          new GetFieldMappingsRequest().indices(query.index).fields(query.field),
          new ActionListener[GetFieldMappingsResponse] {
            override def onResponse(response: GetFieldMappingsResponse): Unit =
              try {
                val srcMap = response.mappings
                  .get(query.index)
                  .get("_doc")
                  .get(query.field)
                  .sourceAsMap()
                  .get(query.field)
                  .asInstanceOf[java.util.Map[String, AnyRef]]
                val srcJson = javaMapEncoder(srcMap)
                val mapping: Mapping = ElasticsearchCodec.decodeGet[Mapping](srcJson)
                mappingCache.put((query.index, query.field), mapping)
                supplier.set(copy(mappingOpt = Some(mapping)))
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

  private def rewriteGetVector(c: QueryRewriteContext, ixv: api.Vector.IndexedVector): QueryBuilder = {
    def ex(e: Exception) = new RuntimeException(s"Failed to retrieve vector at index [${ixv.index}] id [${ixv.id}] field [${ixv.field}]", e)
    val supplier = new SetOnce[KnnQueryBuilder]()
    c.registerAsyncAction((client: Client, l: ActionListener[_]) => {
      client.execute(
        GetAction.INSTANCE,
        new GetRequest(ixv.index, ixv.id),
        new ActionListener[GetResponse] {
          override def onResponse(response: GetResponse): Unit =
            try {
              val srcMap = response.getSourceAsMap
                .get(ixv.field)
                .asInstanceOf[java.util.Map[String, AnyRef]]
              val srcJson: Json = javaMapEncoder(srcMap)
              val vector = ElasticsearchCodec.decodeGet[api.Vector](srcJson)
              supplier.set(copy(query.copy(vector = vector)))
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

  private def sparseBool(c: QueryShardContext, m: Mapping.SparseBoolVector, v: api.Vector.SparseBoolVector): Query =
    m.modelOptions match {
      case Some(SparseBoolVectorModelOptions.JaccardIndexed)          => ???
      case Some(SparseBoolVectorModelOptions.JaccardLsh(bands, rows)) => ???
    }

  private def denseFloat(c: QueryShardContext, m: Mapping.DenseFloatVector, v: api.Vector.DenseFloatVector): Query = {
    ???
  }

}
