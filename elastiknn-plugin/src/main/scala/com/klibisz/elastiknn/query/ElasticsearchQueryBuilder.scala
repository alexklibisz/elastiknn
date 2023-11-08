package com.klibisz.elastiknn.query

import com.google.common.io.BaseEncoding
import com.klibisz.elastiknn.ElastiknnException.ElastiknnRuntimeException
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.{ELASTIKNN_NAME, api}
import org.apache.lucene.search.Query
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.get.{GetAction, GetRequest, GetResponse}
import org.elasticsearch.client.internal.Client
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.index.query._
import org.elasticsearch.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.{ElasticsearchException, ResourceNotFoundException, TransportVersion}

import java.util.Objects
import java.util.concurrent.atomic.AtomicReference

object ElasticsearchQueryBuilder {

  val NAME: String = s"${ELASTIKNN_NAME}_nearest_neighbors"

  private val b64 = BaseEncoding.base64()
  def encodeB64[T: XContentCodec.Encoder](t: T): String = b64.encode(XContentCodec.encodeUnsafeToByteArray(t))
  def decodeB64[T: XContentCodec.Decoder](s: String): T = XContentCodec.decodeUnsafeFromByteArray(b64.decode(s))

  final class Reader(elastiknnQueryBuilder: ElastiknnQueryBuilder) extends Writeable.Reader[ElasticsearchQueryBuilder] {
    override def read(in: StreamInput): ElasticsearchQueryBuilder = {
      in.readFloat() // boost
      in.readOptionalString() // query name
      val query = decodeB64[NearestNeighborsQuery](in.readString())
      new ElasticsearchQueryBuilder(query, elastiknnQueryBuilder)
    }
  }

  final class Parser(elastiknnQueryBuilder: ElastiknnQueryBuilder) extends QueryParser[ElasticsearchQueryBuilder] {
    override def fromXContent(parser: XContentParser): ElasticsearchQueryBuilder = {
      val query = XContentCodec.decodeUnsafe[NearestNeighborsQuery](parser)
      // Account for sparse bool vecs which need to be sorted.
      val sortedVec = query.vec match {
        case v: Vec.SparseBool if !v.isSorted => v.sorted()
        case _                                => query.vec
      }
      new ElasticsearchQueryBuilder(query.withVec(sortedVec), elastiknnQueryBuilder)
    }
  }

  // Used by rewriteGetVector to asynchronously provide an ElasticsearchQueryBuilder for an indexed vector.
  // The doRewrite method will delay indefinitely until until the query builder is provided.
  private final class IndexedVectorQueryBuilder(indexedVec: Vec.Indexed)
    extends AbstractQueryBuilder[IndexedVectorQueryBuilder] {

    private val ref = new AtomicReference[ElasticsearchQueryBuilder](null)

    def set(qb: ElasticsearchQueryBuilder): Unit = ref.set(qb)

    override def doRewrite(c: QueryRewriteContext): QueryBuilder = {
      // If the reference has not been populated, we just return the current instance.
      // Eventually the reference has been set, and we return real query builder.
      // Elasticsearch will retry up to a fixed number of times until it gets the real one.
      val maybe = ref.get()
      if (maybe == null) this else maybe
    }

    def doWriteTo(out: StreamOutput): Unit =
      throw new UnsupportedOperationException("Only supports doRewrite")

    def doXContent(b: XContentBuilder, p: ToXContent.Params): Unit = ()

    def doToQuery(context: SearchExecutionContext): Query =
      throw new UnsupportedOperationException("Only supports doRewrite")

    def doEquals(other: IndexedVectorQueryBuilder): Boolean =
      throw new UnsupportedOperationException("Only supports doRewrite")

    def doHashCode(): Int =
      throw new UnsupportedOperationException("Only supports doRewrite")

    def getWriteableName: String = s"IndexedVectorQueryBuilder(${indexedVec})"

    def getMinimalSupportedVersion: TransportVersion =
      throw new UnsupportedOperationException("Only supports doRewrite")
  }
}

final class ElasticsearchQueryBuilder(val query: NearestNeighborsQuery, elastiknnQueryBuilder: ElastiknnQueryBuilder)
    extends AbstractQueryBuilder[ElasticsearchQueryBuilder] {

  override def doWriteTo(out: StreamOutput): Unit = {
    out.writeString(ElasticsearchQueryBuilder.encodeB64(query))
  }

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doRewrite(context: QueryRewriteContext): QueryBuilder =
    query.vec match {
      case ixv: Vec.Indexed => rewriteGetVector(context, ixv)
      case _                => this
    }

  override def doToQuery(context: SearchExecutionContext): Query =
    elastiknnQueryBuilder.build(query, context).map(_.toLuceneQuery(context.getIndexReader)).get

  override def doEquals(other: ElasticsearchQueryBuilder): Boolean = other.query == this.query

  override def doHashCode(): Int = Objects.hash(query)

  override def getWriteableName: String = ElasticsearchQueryBuilder.NAME

  private def rewriteGetVector(c: QueryRewriteContext, ixv: api.Vec.Indexed): QueryBuilder = {
    def doesNotExist: ResourceNotFoundException =
      new ResourceNotFoundException(s"Document with id [${ixv.id}] in index [${ixv.index}] not found")
    def doesNotHaveField: ResourceNotFoundException =
      new ResourceNotFoundException(s"Document with id [${ixv.id}] in index [${ixv.index}] exists, but does not have field [${ixv.field}]")
    def unexpectedFieldType: ResourceNotFoundException =
      new ResourceNotFoundException(
        s"Document with id [${ixv.id}] in index [${ixv.index}] exists, but the field [${ixv.field}] has an unexpected type"
      )
    def unexpected(e: Exception): ElastiknnRuntimeException =
      new ElastiknnRuntimeException(s"Failed to retrieve vector at index [${ixv.index}] id [${ixv.id}] field [${ixv.field}]", e)

    // Instantiate a placeholder query builder which will be provided with the real query builder after
    // the indexed vector has been asynchronously fetched.
    val queryBuilder = new ElasticsearchQueryBuilder.IndexedVectorQueryBuilder(ixv)

    // Request the actual document in order to construct the query
    c.registerAsyncAction((client: Client, listener: ActionListener[_]) => {
      client.execute(
        GetAction.INSTANCE,
        new GetRequest(ixv.index, ixv.id),
        new ActionListener[GetResponse] {
          override def onResponse(response: GetResponse): Unit = {
            val asMap = response.getSourceAsMap
            if (!response.isExists || asMap == null) listener.onFailure(doesNotExist)
            else if (!asMap.containsKey(ixv.field)) listener.onFailure(doesNotHaveField)
            else {
              // Have to handle both vector JSON formats: object (map) and array (list).
              // It would be great if we could just get an XContentParser of the document body, but seems we cannot.
              val field: Any = asMap.get(ixv.field)
              field match {
                case map: java.util.Map[String @unchecked, Object @unchecked] if map.isInstanceOf[java.util.Map[String, Object]] =>
                  val vec = XContentCodec.decodeUnsafeFromMap[Vec](map)
                  queryBuilder.set(new ElasticsearchQueryBuilder(query.withVec(vec), elastiknnQueryBuilder))
                  listener.asInstanceOf[ActionListener[Any]].onResponse(null)
                case lst: java.util.List[Object @unchecked] if lst.isInstanceOf[java.util.List[Object]] =>
                  val vec = XContentCodec.decodeUnsafeFromList[Vec](lst)
                  queryBuilder.set(new ElasticsearchQueryBuilder(query.withVec(vec), elastiknnQueryBuilder))
                  listener.asInstanceOf[ActionListener[Any]].onResponse(null)
                case _ => listener.onFailure(unexpectedFieldType)
              }
            }
          }
          override def onFailure(e: Exception): Unit = e match {
            case _: ElasticsearchException => listener.onFailure(e)
            case _: Exception              => listener.onFailure(unexpected(e))
          }
        }
      )
    })

    queryBuilder
  }

  override def getMinimalSupportedVersion: TransportVersion = TransportVersion.current()

}
