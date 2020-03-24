package com.klibisz.elastiknn.mapper

import java.util

import com.klibisz.elastiknn.api.{ElasticsearchCodec, Mapping, SparseBoolVectorModelOptions}
import com.klibisz.elastiknn.{ELASTIKNN_NAME, api}
import com.klibisz.elastiknn.VectorDimensionException
import com.klibisz.elastiknn.query.ExactSimilarityQuery
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, JsonObject}
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index.IndexableField
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Query}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder}
import org.elasticsearch.index.mapper.Mapper.TypeParser
import org.elasticsearch.index.mapper._
import org.elasticsearch.index.query.QueryShardContext

object VectorMapper {
  val sparseBoolVector: VectorMapper[api.Mapping.SparseBoolVector, api.Vector.SparseBoolVector] =
    new VectorMapper[api.Mapping.SparseBoolVector, api.Vector.SparseBoolVector] {
      override val CONTENT_TYPE: String = s"${ELASTIKNN_NAME}_sparse_bool_vector"
      override def index(mapping: api.Mapping.SparseBoolVector, vec: api.Vector.SparseBoolVector, doc: ParseContext.Document): Unit = {
        if (vec.totalIndices != mapping.dims) throw VectorDimensionException(vec.totalIndices, mapping.dims)
        ExactSimilarityQuery.index(vec).foreach(doc.add)
        mapping.modelOptions match {
          case Some(SparseBoolVectorModelOptions.JaccardIndexed)          =>
          case Some(SparseBoolVectorModelOptions.JaccardLsh(bands, rows)) =>
        }
      }
    }
  val denseFloatVector: VectorMapper[api.Mapping.DenseFloatVector, api.Vector.DenseFloatVector] =
    new VectorMapper[api.Mapping.DenseFloatVector, api.Vector.DenseFloatVector] {
      override val CONTENT_TYPE: String = s"${ELASTIKNN_NAME}_dense_float_vector"
      override def index(mapping: Mapping.DenseFloatVector, vec: api.Vector.DenseFloatVector, doc: ParseContext.Document): Unit = {
        ()
      }
    }
}

abstract class VectorMapper[M <: api.Mapping: ElasticsearchCodec, V <: api.Vector: ElasticsearchCodec] {

  val CONTENT_TYPE: String
  def index(mapping: M, vec: V, doc: ParseContext.Document): Unit

  private val fieldType = new this.FieldType
  private val mapper = this

  import com.klibisz.elastiknn.utils.CirceUtils.javaMapEncoder

  class TypeParser extends Mapper.TypeParser {

    override def parse(name: String, node: util.Map[String, AnyRef], parserContext: TypeParser.ParserContext): Mapper.Builder[_, _] = {
      val mappingTry = implicitly[ElasticsearchCodec[M]].decodeJson(node.asJson).toTry
      val builder = new Builder(name, mappingTry.get)
      TypeParsers.parseField(builder, name, node, parserContext)
      node.clear()
      builder
    }
  }

  private class Builder(name: String, mapping: M) extends FieldMapper.Builder[Builder, FieldMapper](name, fieldType, fieldType) {

    /** Populate the given builder from the given Json. */
    private def populateBuilder(json: Json, builder: XContentBuilder): Unit = {
      def populate(json: Json): Unit =
        if (json.isBoolean) json.asBoolean.foreach(builder.value)
        else if (json.isString) json.asString.foreach(builder.value)
        else if (json.isNumber) json.asNumber.foreach { n =>
          lazy val asInt = n.toInt
          lazy val asLong = n.toLong
          if (asInt.isDefined) builder.value(asInt.get)
          else if (asLong.isDefined) builder.value(asLong.get)
          else builder.value(n.toDouble)
        } else if (json.isArray) json.asArray.foreach(_.foreach(populate))
        else if (json.isObject) json.asObject.foreach {
          _.toIterable.foreach {
            case (k, v) =>
              if (v.isObject) {
                builder.startObject(k)
                populate(v)
                builder.endObject()
              } else if (v.isArray) {
                builder.startArray(k)
                populate(v)
                builder.endArray()
              } else {
                builder.field(k)
                populate(v)
              }
          }
        }
      populate(json)
    }

    override def build(context: Mapper.BuilderContext): FieldMapper = {
      super.setupFieldType(context)

      new FieldMapper(name, fieldType, defaultFieldType, context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo) {
        override def parse(context: ParseContext): Unit = {
          val doc: ParseContext.Document = context.doc()
          val json: Json = context.parser().map().asJson
          val vecTry = implicitly[ElasticsearchCodec[V]].decodeJson(json).toTry
          mapper.index(mapping, vecTry.get, doc)
        }
        override def parseCreateField(context: ParseContext, fields: util.List[IndexableField]): Unit =
          throw new IllegalStateException("parse() is implemented directly")
        override def contentType(): String = CONTENT_TYPE
        override def doXContentBody(builder: XContentBuilder, includeDefaults: Boolean, params: ToXContent.Params): Unit = {
          super.doXContentBody(builder, includeDefaults, params)
          implicitly[ElasticsearchCodec[M]]
            .encode(mapping)
            .asObject
            .map(_.toIterable.filter(_._1 != "type"))
            .map(JsonObject.fromIterable)
            .map(Json.fromJsonObject)
            .foreach(populateBuilder(_, builder))

        }
      }
    }
  }

  class FieldType extends MappedFieldType {
    override def typeName(): String = CONTENT_TYPE
    override def clone(): FieldType = new FieldType
    override def termQuery(value: Any, context: QueryShardContext): Query =
      throw new UnsupportedOperationException(s"Field [${name()}] of type [${typeName()}] doesn't support queries")
    override def existsQuery(context: QueryShardContext): Query = new DocValuesFieldExistsQuery(name())
  }

}
