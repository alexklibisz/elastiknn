package com.klibisz.elastiknn.mapper

import java.util

import com.klibisz.elastiknn.{ELASTIKNN_NAME, api}
import com.klibisz.elastiknn.api.Mapping
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.apache.lucene.index.IndexableField
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Query}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder}
import org.elasticsearch.index.mapper.Mapper.TypeParser
import org.elasticsearch.index.mapper._
import org.elasticsearch.index.query.QueryShardContext

import scala.util.Failure

object VectorMapper {
  val sparseBoolVector: VectorMapper[api.Mapping.ElastiknnSparseBoolVector] =
    new VectorMapper[api.Mapping.ElastiknnSparseBoolVector](s"${ELASTIKNN_NAME}_sparse_bool_vector") {
      override def parse(context: ParseContext, options: api.Mapping.ElastiknnSparseBoolVector): Unit = {
        ???
      }
    }
  val denseFloatVector: VectorMapper[api.Mapping.ElastiknnDenseFloatVector] =
    new VectorMapper[api.Mapping.ElastiknnDenseFloatVector](s"${ELASTIKNN_NAME}_dense_float_vector") {
      override def parse(context: ParseContext, mapping: Mapping.ElastiknnDenseFloatVector): Unit = {
        ???
      }
    }
}

abstract class VectorMapper[M <: api.Mapping](val CONTENT_TYPE: String) {

  def parse(context: ParseContext, mapping: M): Unit

  private val fieldType = new this.FieldType
  private val mapper = this

  class TypeParser extends Mapper.TypeParser {

    import com.klibisz.elastiknn.utils.CirceUtils.javaMapEncoder

    override def parse(name: String, node: util.Map[String, AnyRef], parserContext: TypeParser.ParserContext): Mapper.Builder[_, _] = {
      val builder = new Builder(name)
      TypeParsers.parseField(builder, name, node, parserContext)
      implicitly[Decoder[Mapping]]
        .decodeJson(node.asJson)
        .map(_.asInstanceOf[M])
        .foreach(builder.mapping = _)
      node.clear()
//      if (builder.mapping != null) {
//        implicitly[Encoder[Mapping]]
//          .apply(builder.mapping)
//          .asObject
//          .foreach(_.keys.foreach(node.remove))
//      }
      builder
    }
  }

  private class Builder(name: String) extends FieldMapper.Builder[Builder, FieldMapper](name, fieldType, fieldType) {

    var mapping: M = _

    override def build(context: Mapper.BuilderContext): FieldMapper = {
      super.setupFieldType(context)

      new FieldMapper(name, fieldType, defaultFieldType, context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo) {
        override def parse(context: ParseContext): Unit = mapper.parse(context, mapping)
        override def parseCreateField(context: ParseContext, fields: util.List[IndexableField]): Unit =
          throw new IllegalStateException("parse() is implemented directly")
        override def contentType(): String = CONTENT_TYPE
        override def doXContentBody(builder: XContentBuilder, includeDefaults: Boolean, params: ToXContent.Params): Unit = {
          super.doXContentBody(builder, includeDefaults, params)

          builder.field("dims", 100)
          builder.field("model_options", new util.HashMap[String, AnyRef] {
            put("type", "jaccard_lsh")
            put("bands", 100.asInstanceOf[AnyRef])
            put("rows", 1.asInstanceOf[AnyRef])
          })

//          if (mapping != null) {
//            implicitly[Encoder[Mapping]]
//              .apply(mapping)
//              .asObject
//              .foreach(_.toIterable.foreach {
//                case (k, v) => if (k != "type") ()
//              })
//          }

          // builder.field("wtf", 99)
//          mapping match {
//            case Mapping.ElastiknnSparseBoolVector(dims, _) =>
////              builder.field("dims", dims)
////              builder.field("wtf", 99)
//            case Mapping.ElastiknnDenseFloatVector(dims, _) =>
////              builder.field("dims", dims)
////              builder.field("wtf", 99)
//            case _ => ()
//          }
        }

//        override def doXContentDocValues(builder: XContentBuilder, includeDefaults: Boolean): Unit =
//          super.doXContentDocValues(builder, includeDefaults)
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
