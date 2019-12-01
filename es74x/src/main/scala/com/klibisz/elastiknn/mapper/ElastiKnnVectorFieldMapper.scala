package com.klibisz.elastiknn.mapper

import java.util

import com.klibisz.elastiknn._
import com.klibisz.elastiknn.utils.CirceUtils.mapEncoder
import io.circe.syntax._
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index._
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Query, SortField}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.fielddata.IndexFieldData.XFieldComparatorSource
import org.elasticsearch.index.fielddata.plain.DocValuesIndexFieldData
import org.elasticsearch.index.mapper.FieldMapper.CopyTo
import org.elasticsearch.index.mapper.Mapper.TypeParser.ParserContext
import org.elasticsearch.index.mapper._
import org.elasticsearch.index.query.QueryShardContext
import org.elasticsearch.index.{Index, IndexSettings, fielddata}
import org.elasticsearch.indices.breaker.CircuitBreakerService
import org.elasticsearch.search.MultiValueMode
import scalapb_circe.JsonFormat

/**
  * Custom "elastiknn_vector" type which stores ElastiKnnVectors without modifying their order.
  * Based heavily on the DenseVectorFieldMapper and related classes in Elasticsearch.
  * See for context:https://github.com/elastic/elasticsearch/issues/49695
  */
object ElastiKnnVectorFieldMapper {

  val CONTENT_TYPE = s"${ELASTIKNN_NAME}_vector"

  private val fieldType = new FieldType

  class TypeParser extends Mapper.TypeParser {

    /** This method gets called when you create a mapping with this type. */
    override def parse(name: String,
                       node: util.Map[String, AnyRef],
                       parserContext: ParserContext): Mapper.Builder[_, _] =
      new Builder(name)
  }

  class Builder(name: String)
      extends FieldMapper.Builder[ElastiKnnVectorFieldMapper.Builder,
                                  ElastiKnnVectorFieldMapper](name,
                                                              fieldType,
                                                              fieldType) {
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

    override def fielddataBuilder(
        indexName: String): fielddata.IndexFieldData.Builder =
      FieldData.Builder
  }

  object FieldData {
    object Builder extends fielddata.IndexFieldData.Builder {
      override def build(
          indexSettings: IndexSettings,
          fieldType: MappedFieldType,
          cache: fielddata.IndexFieldDataCache,
          breakerService: CircuitBreakerService,
          mapperService: MapperService): fielddata.IndexFieldData[_] =
        new FieldData(indexSettings.getIndex, fieldType.name)
    }
  }

  class FieldData(index: Index, fieldName: String)
      extends DocValuesIndexFieldData(index, fieldName)
      with fielddata.IndexFieldData[AtomicFieldData] {
    override def load(context: LeafReaderContext): AtomicFieldData =
      new AtomicFieldData(context.reader(), fieldName)

    override def loadDirect(context: LeafReaderContext): AtomicFieldData =
      load(context)

    override def sortField(missingValue: Any,
                           sortMode: MultiValueMode,
                           nested: XFieldComparatorSource.Nested,
                           reverse: Boolean): SortField =
      throw new UnsupportedOperationException("Sorting is not supported")
  }

  class AtomicFieldData(reader: LeafReader, field: String)
      extends fielddata.AtomicFieldData {

    private lazy val bdv: BinaryDocValues = DocValues.getBinary(reader, field)

    override def getScriptValues: fielddata.ScriptDocValues[_] =
      new ScriptDocValues(bdv)

    override def getBytesValues: fielddata.SortedBinaryDocValues =
      throw new UnsupportedOperationException(
        "String representation of doc values for elastiknn_vector fields is not supported")

    override def ramBytesUsed(): Long = 0L

    override def close(): Unit = ()
  }

  class ScriptDocValues(in: BinaryDocValues)
      extends fielddata.ScriptDocValues[Any] {

    private var ekv: Option[ElastiKnnVector] = None

    override def setNextDocId(docId: Int): Unit =
      if (in.advanceExact(docId))
        ekv = Some(ElastiKnnVector.parseFrom(in.binaryValue.bytes))
      else ekv = None

    override def get(i: Int): Any = ekv match {
      case Some(ElastiKnnVector(ElastiKnnVector.Vector.DoubleVector(v))) =>
        v.values(i)
      case Some(ElastiKnnVector(ElastiKnnVector.Vector.BoolVector(v))) =>
        v.values(i)
      case _ =>
        throw new IllegalStateException(
          s"Couldn't parse a valid ElastiKnnVector, found: $ekv")
    }

    override def size(): Int = ekv match {
      case Some(_) => 1
      case None    => 0
    }
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
                                fields: util.List[IndexableField]): Unit =
    throw new IllegalStateException("parse is implemented directly")

  override def contentType(): String = {
    ElastiKnnVectorFieldMapper.CONTENT_TYPE
  }
}
