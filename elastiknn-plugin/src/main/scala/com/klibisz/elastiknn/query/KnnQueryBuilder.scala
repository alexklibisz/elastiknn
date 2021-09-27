package com.klibisz.elastiknn.query

import com.google.common.io.BaseEncoding
import com.klibisz.elastiknn.ElastiknnException.ElastiknnRuntimeException
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.utils.CirceUtils
import com.klibisz.elastiknn.{ELASTIKNN_NAME, api}
import io.circe.Json
import org.apache.lucene.search.Query
import org.apache.lucene.util.SetOnce
import org.elasticsearch.{ElasticsearchException, ResourceNotFoundException}
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.get.{GetAction, GetRequest, GetResponse}
import org.elasticsearch.client.Client
import org.elasticsearch.common.io.stream.{StreamInput, StreamOutput, Writeable}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentParser}
import org.elasticsearch.index.query._

import java.util.Objects

object KnnQueryBuilder {

  val NAME: String = s"${ELASTIKNN_NAME}_nearest_neighbors"

  private val b64 = BaseEncoding.base64()
  def encodeB64[T: ElasticsearchCodec](t: T): String = b64.encode(encode(t).noSpaces.getBytes)
  def decodeB64[T: ElasticsearchCodec](s: String): T = parse(new String(b64.decode(s))).flatMap(decodeJson[T]).toTry.get

  object Reader extends Writeable.Reader[KnnQueryBuilder] {
    override def read(in: StreamInput): KnnQueryBuilder = {
      in.readFloat() // boost
      in.readOptionalString() // query name
      val query = decodeB64[NearestNeighborsQuery](in.readString())
      new KnnQueryBuilder(query)
    }
  }

  object Parser extends QueryParser[KnnQueryBuilder] {
    override def fromXContent(parser: XContentParser): KnnQueryBuilder = {
      val map = parser.map()
      val json: Json = CirceUtils.javaMapEncoder(map)
      val query = ElasticsearchCodec.decodeJsonGet[NearestNeighborsQuery](json)
      // Account for sparse bool vecs which need to be sorted.
      val sortedVec = query.vec match {
        case v: Vec.SparseBool if !v.isSorted => v.sorted()
        case _                                => query.vec
      }
      new KnnQueryBuilder(query.withVec(sortedVec))
    }
  }

}

final case class KnnQueryBuilder(query: NearestNeighborsQuery) extends AbstractQueryBuilder[KnnQueryBuilder] {

  override def doWriteTo(out: StreamOutput): Unit = {
    out.writeString(KnnQueryBuilder.encodeB64(query))
  }

  override def doXContent(builder: XContentBuilder, params: ToXContent.Params): Unit = ()

  override def doRewrite(context: QueryRewriteContext): QueryBuilder =
    query.vec match {
      case ixv: Vec.Indexed => rewriteGetVector(context, ixv)
      case _                => this
    }

  override def doToQuery(context: SearchExecutionContext): Query =
    ElastiknnQuery(query, context).map(_.toLuceneQuery(context.getIndexReader)).get

  override def doEquals(other: KnnQueryBuilder): Boolean = other.query == this.query

  override def doHashCode(): Int = Objects.hash(query)

  override def getWriteableName: String = KnnQueryBuilder.NAME

  private def rewriteGetVector(c: QueryRewriteContext, ixv: api.Vec.Indexed): QueryBuilder = {
    def doesNotExist: ResourceNotFoundException =
      new ResourceNotFoundException(s"Document with id [${ixv.id}] in index [${ixv.index}] not found")
    def doesNotHaveField: ResourceNotFoundException =
      new ResourceNotFoundException(s"Document with id [${ixv.id}] in index [${ixv.index}] exists, but does not have field [${ixv.field}]")
    def unexpected(e: Exception): ElastiknnRuntimeException =
      new ElastiknnRuntimeException(s"Failed to retrieve vector at index [${ixv.index}] id [${ixv.id}] field [${ixv.field}]", e)

    val supplier = new SetOnce[KnnQueryBuilder]()

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
              val srcField: Any = response.getSourceAsMap.get(ixv.field)
              val srcJson: Json = CirceUtils.encodeAny(srcField)
              val vector = ElasticsearchCodec.decodeJsonGet[api.Vec](srcJson)
              supplier.set(copy(query.withVec(vector)))
              listener.asInstanceOf[ActionListener[Any]].onResponse(null)
            }
          }
          override def onFailure(e: Exception): Unit = e match {
            case _: ElasticsearchException => listener.onFailure(e)
            case _: Exception              => listener.onFailure(unexpected(e))
          }
        }
      )
    })

    RewriteQueryBuilder(_ => supplier.get())
  }
}
