package com.klibisz.elastiknn.mapper

import java.util

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api.Mapping
import io.circe.syntax._
import org.apache.lucene.index.IndexableField
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Query}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder}
import org.elasticsearch.index.mapper.Mapper.TypeParser
import org.elasticsearch.index.mapper._
import org.elasticsearch.index.query.QueryShardContext

import scala.collection.JavaConverters._

object VectorMapper {
  val sparseBoolVector = new VectorMapper(s"${ELASTIKNN_NAME}_sparse_bool_vector")
  val denseFloatVector = new VectorMapper(s"${ELASTIKNN_NAME}_dense_float_vector")

  private object Fields {
    val DIMS: String = "dims"
    val MODEL_OPTIONS: String = "model_options"
  }

}

class VectorMapper private (val CONTENT_TYPE: String) {

  import VectorMapper._

  private val fieldType = new this.FieldType

  class TypeParser extends Mapper.TypeParser {

    import com.klibisz.elastiknn.utils.CirceUtils.javaMapEncoder

    override def parse(name: String, node: util.Map[String, AnyRef], parserContext: TypeParser.ParserContext): Mapper.Builder[_, _] = {
      val builder = new Builder(name)
      TypeParsers.parseField(builder, name, node, parserContext)
      val iter = node.entrySet.iterator
      while (iter.hasNext) {
        val entry = iter.next()
        (entry.getKey, entry.getValue) match {
          case (Fields.DIMS, i: Integer) =>
            iter.remove()
          case (Fields.MODEL_OPTIONS, m: Map[String, AnyRef]) =>
            iter.remove()
        }
      }

//      if (node.keySet().asScala != Set("type")) {
//        val json = node.asJson(javaMapEncoder)
//        builder.mapping = Mapping.dec.decodeJson(json).toTry.get
//      }
//      val iter = node.entrySet().iterator()
//      while (iter.hasNext) {
//        val next = iter.next()
//        if (next.getKey != "type") iter.remove()
//      }
      builder
    }
  }

  private class Builder(name: String) extends FieldMapper.Builder[Builder, FieldMapper](name, fieldType, fieldType) {

    var dims: Int = _
//    var mapping: Mapping = _

    override def build(context: Mapper.BuilderContext): FieldMapper = {
      super.setupFieldType(context)
      new FieldMapper(name, fieldType, defaultFieldType, context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo) {
        override def parse(context: ParseContext): Unit = {
          println(dims)
//          println(mapping)
          ()
        }
        override def parseCreateField(context: ParseContext, fields: util.List[IndexableField]): Unit =
          throw new IllegalStateException("parse() is implemented directly")
        override def contentType(): String = CONTENT_TYPE

        override def doXContentBody(builder: XContentBuilder, includeDefaults: Boolean, params: ToXContent.Params): Unit = {
          super.doXContentBody(builder, includeDefaults, params)
          builder.field(Fields.DIMS, dims)
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
