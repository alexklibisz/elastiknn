package com.klibisz.elastiknn.mapper

import java.util

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.api.{DenseFloatVectorModelOptions, SparseBoolVectorModelOptions}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.apache.lucene.index.IndexableField
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Query}
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder}
import org.elasticsearch.index.mapper.Mapper.TypeParser
import org.elasticsearch.index.mapper._
import org.elasticsearch.index.query.QueryShardContext

import scala.util.Try

object VectorMapper {
  val sparseBoolVector: VectorMapper[SparseBoolVectorModelOptions] =
    new VectorMapper[SparseBoolVectorModelOptions](s"${ELASTIKNN_NAME}_sparse_bool_vector") {
      override def parse(context: ParseContext, dims: Int, options: SparseBoolVectorModelOptions): Unit = {
        ???
      }
    }
  val denseFloatVector: VectorMapper[DenseFloatVectorModelOptions] =
    new VectorMapper[DenseFloatVectorModelOptions](s"${ELASTIKNN_NAME}_dense_float_vector") {
      override def parse(context: ParseContext, dims: Int, options: DenseFloatVectorModelOptions): Unit = {
        ???
      }
    }

  private object Keys {
    val DIMS: String = "dims"
    val MODEL_OPTIONS: String = "model_options"
  }

}

abstract class VectorMapper[MODEL_OPTIONS: Encoder: Decoder](val CONTENT_TYPE: String) {

  import VectorMapper._

  def parse(context: ParseContext, dims: Int, options: MODEL_OPTIONS): Unit

  private val fieldType = new this.FieldType
  private val mapper = this

  class TypeParser extends Mapper.TypeParser {

    import com.klibisz.elastiknn.utils.CirceUtils.javaMapEncoder

    override def parse(name: String, node: util.Map[String, AnyRef], parserContext: TypeParser.ParserContext): Mapper.Builder[_, _] = {
      val builder = new Builder(name)
      TypeParsers.parseField(builder, name, node, parserContext)
      // You have to set mutable fields by iterating over the map. For some reason this method gets called > 1 times and
      // the node map doesn't always include all of the entries so you can't convert it to JSON and then to a case class.
      val iter = node.entrySet.iterator()
      while (iter.hasNext) {
        val entry = iter.next()
        (entry.getKey, entry.getValue) match {
          case (Keys.DIMS, i: Integer) =>
            builder.dims = i
            iter.remove()
          case (Keys.MODEL_OPTIONS, m: util.Map[_, _]) if Try(m.asInstanceOf[util.Map[String, AnyRef]]).isSuccess =>
            builder.optionsMap = m.asInstanceOf[java.util.Map[String, AnyRef]]
            val json = builder.optionsMap.asJson(javaMapEncoder)
            builder.options = implicitly[Decoder[MODEL_OPTIONS]].decodeJson(json).toTry.get
            iter.remove()
          case _ =>
        }
      }
      builder
    }
  }

  private class Builder(name: String) extends FieldMapper.Builder[Builder, FieldMapper](name, fieldType, fieldType) {

    var dims: Int = _
    var options: MODEL_OPTIONS = _
    var optionsMap: java.util.Map[String, AnyRef] = _

    override def build(context: Mapper.BuilderContext): FieldMapper = {
      super.setupFieldType(context)

      new FieldMapper(name, fieldType, defaultFieldType, context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo) {
        override def parse(context: ParseContext): Unit = mapper.parse(context, dims, options)
        override def parseCreateField(context: ParseContext, fields: util.List[IndexableField]): Unit =
          throw new IllegalStateException("parse() is implemented directly")
        override def contentType(): String = CONTENT_TYPE
        override def doXContentBody(builder: XContentBuilder, includeDefaults: Boolean, params: ToXContent.Params): Unit = {
          super.doXContentBody(builder, includeDefaults, params)
          builder.field(Keys.DIMS, dims)
          builder.field(Keys.MODEL_OPTIONS, optionsMap)
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
