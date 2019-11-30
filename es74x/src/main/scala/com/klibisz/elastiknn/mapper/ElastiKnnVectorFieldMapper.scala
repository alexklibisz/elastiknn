package com.klibisz.elastiknn.mapper

import java.util

import com.klibisz.elastiknn._
import com.klibisz.elastiknn.utils.CirceUtils.mapEncoder
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index.IndexableField
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Query}
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.mapper.FieldMapper.CopyTo
import org.elasticsearch.index.mapper.Mapper.TypeParser.ParserContext
import org.elasticsearch.index.mapper._
import org.elasticsearch.index.query.QueryShardContext
import io.circe.syntax._
import org.apache.lucene.util.BytesRef
import scalapb_circe.JsonFormat

/** Based on DenseVectorFieldMapper in Elasticsearch. */
object ElastiKnnVectorFieldMapper {

  val CONTENT_TYPE = s"${ELASTIKNN_NAME}_vector"

  object Defaults {
    lazy val FIELD_TYPE = new FieldType()
  }

  class TypeParser extends Mapper.TypeParser {

    /** This method gets called when you create a mapping with this type. */
    override def parse(name: String,
                       node: util.Map[String, AnyRef],
                       parserContext: ParserContext): Mapper.Builder[_, _] =
      new Builder(name)
  }

  class Builder(name: String)
      extends FieldMapper.Builder[ElastiKnnVectorFieldMapper.Builder,
                                  ElastiKnnVectorFieldMapper](
        name,
        Defaults.FIELD_TYPE,
        Defaults.FIELD_TYPE) {
    override def build(
        context: Mapper.BuilderContext): ElastiKnnVectorFieldMapper = {
      super.setupFieldType(context)
      new ElastiKnnVectorFieldMapper(name,
                                     fieldType,
                                     defaultFieldType,
                                     context.indexSettings(),
                                     multiFieldsBuilder.build(this, context),
                                     copyTo)
    }
  }

  class FieldType extends MappedFieldType {
    override def typeName(): String = CONTENT_TYPE

    override def clone(): FieldType = new FieldType()

    override def termQuery(value: Any, context: QueryShardContext): Query =
      throw new UnsupportedOperationException(
        s"Field [${name()}] of type [${typeName()}] doesn't support queries")

    override def existsQuery(context: QueryShardContext): Query =
      new DocValuesFieldExistsQuery(name())
  }

}

class ElastiKnnVectorFieldMapper(simpleName: String,
                                 fieldType: MappedFieldType,
                                 defaultFieldType: MappedFieldType,
                                 indexSettings: Settings,
                                 multiFields: FieldMapper.MultiFields,
                                 copyTo: CopyTo)
    extends FieldMapper(simpleName,
                        fieldType,
                        defaultFieldType,
                        indexSettings,
                        multiFields,
                        copyTo) {

  override def parse(context: ParseContext): Unit = {
    val json = context.parser.map.asJson(mapEncoder)
    val ekv = JsonFormat.fromJson[ElastiKnnVector](json)
    val name = fieldType.name()
    val field = new BinaryDocValuesField(name, new BytesRef(ekv.toByteArray))
    context.doc.addWithKey(name, field)
  }

  override def parseCreateField(context: ParseContext,
                                fields: util.List[IndexableField]): Unit = {
    throw new AssertionError("parse is implemented directly")
  }

  override def contentType(): String = {
    ElastiKnnVectorFieldMapper.CONTENT_TYPE
  }
}
