package com.klibisz.elastiknn.mapper

import java.util

import com.klibisz.elastiknn.api.ElasticsearchCodec._
import com.klibisz.elastiknn.api.{ElasticsearchCodec, JavaJsonMap, Mapping, Vec}
import com.klibisz.elastiknn.query.{ExactSimilarityQuery, LshQuery, SparseIndexedQuery}
import com.klibisz.elastiknn.{ELASTIKNN_NAME, VectorDimensionException}
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import org.apache.lucene.index.IndexableField
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Query}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder}
import org.elasticsearch.index.mapper.Mapper.TypeParser
import org.elasticsearch.index.mapper._
import org.elasticsearch.index.query.QueryShardContext

import scala.util.{Failure, Success, Try}

object VectorMapper {
  val sparseBoolVector: VectorMapper[Vec.SparseBool] =
    new VectorMapper[Vec.SparseBool] {
      override val CONTENT_TYPE: String = s"${ELASTIKNN_NAME}_sparse_bool_vector"
      override def checkAndCreateFields(mapping: Mapping, field: String, vec: Vec.SparseBool): Try[Seq[IndexableField]] =
        if (mapping.dims != vec.totalIndices)
          Failure(VectorDimensionException(vec.totalIndices, mapping.dims))
        else {
          val sorted = vec.sorted() // Sort for faster intersections on the query side.
          mapping match {
            case Mapping.SparseBool(_)    => Try(ExactSimilarityQuery.index(field, sorted))
            case Mapping.SparseIndexed(_) => Try(SparseIndexedQuery.index(field, sorted))
            case m: Mapping.JaccardLsh    => Try(LshQuery.index(field, sorted, m))
            case m: Mapping.HammingLsh    => Try(LshQuery.index(field, sorted, m))
            case _                        => Failure(incompatible(mapping, vec))
          }
        }
    }
  val denseFloatVector: VectorMapper[Vec.DenseFloat] =
    new VectorMapper[Vec.DenseFloat] {
      override val CONTENT_TYPE: String = s"${ELASTIKNN_NAME}_dense_float_vector"
      override def checkAndCreateFields(mapping: Mapping, field: String, vec: Vec.DenseFloat): Try[Seq[IndexableField]] =
        if (mapping.dims != vec.values.length)
          Failure(VectorDimensionException(vec.values.length, mapping.dims))
        else
          mapping match {
            case Mapping.DenseFloat(_) => Try(ExactSimilarityQuery.index(field, vec))
            case m: Mapping.AngularLsh => Try(LshQuery.index(field, vec, m))
            case _                     => Failure(incompatible(mapping, vec))
          }
    }

  private def incompatible(m: Mapping, v: Vec): Exception = new IllegalArgumentException(
    s"Mapping [${nospaces(m)}] is not compatible with vector [${nospaces(v)}]"
  )
}

abstract class VectorMapper[V <: Vec: ElasticsearchCodec] { self =>

  val CONTENT_TYPE: String
  def checkAndCreateFields(mapping: Mapping, field: String, vec: V): Try[Seq[IndexableField]]

  private val fieldType = new this.FieldType

  import com.klibisz.elastiknn.utils.CirceUtils.javaMapEncoder

  class TypeParser extends Mapper.TypeParser {
    override def parse(name: String, node: JavaJsonMap, parserContext: TypeParser.ParserContext): Mapper.Builder[_, _] = {
      val mapping: Mapping = ElasticsearchCodec.decodeJsonGet[Mapping](node.asJson)
      val builder: Builder = new Builder(name, mapping)
      TypeParsers.parseField(builder, name, node, parserContext)
      node.clear()
      builder
    }
  }

  private class Builder(field: String, mapping: Mapping) extends FieldMapper.Builder[Builder, FieldMapper](field, fieldType, fieldType) {

    /** Populate the given builder from the given Json. */
    private def populateXContent(json: Json, builder: XContentBuilder): Unit = {
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

      new FieldMapper(field, fieldType, defaultFieldType, context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo) {
        override def parse(context: ParseContext): Unit = {
          val doc: ParseContext.Document = context.doc()
          val json: Json = context.parser.map.asJson
          val vec = ElasticsearchCodec.decodeJsonGet[V](json)
          val fields = self.checkAndCreateFields(mapping, name, vec).get
          fields.foreach(doc.add)
        }
        override def parseCreateField(context: ParseContext, fields: util.List[IndexableField]): Unit =
          throw new IllegalStateException("parse() is implemented directly")
        override def contentType(): String = CONTENT_TYPE
        override def doXContentBody(builder: XContentBuilder, includeDefaults: Boolean, params: ToXContent.Params): Unit = {
          super.doXContentBody(builder, includeDefaults, params)
          ElasticsearchCodec
            .encode(mapping)
            .asObject
            .map(_.toIterable.filter(_._1 != "type"))
            .map(JsonObject.fromIterable)
            .map(Json.fromJsonObject)
            .foreach(populateXContent(_, builder))

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
