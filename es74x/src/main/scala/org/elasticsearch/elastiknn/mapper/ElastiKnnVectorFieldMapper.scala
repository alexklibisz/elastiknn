package org.elasticsearch.elastiknn.mapper

import java.util

import org.elasticsearch.elastiknn._
import org.elasticsearch.elastiknn.utils.CirceUtils.javaMapEncoder
import org.elasticsearch.elastiknn.utils.Implicits._
import io.circe.syntax._
import org.apache.lucene.document.BinaryDocValuesField
import org.apache.lucene.index._
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Query, SortField}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.elastiknn.SparseBoolVector
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

import scala.util.{Random, Success, Try, Failure}

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
    override def parse(name: String, node: util.Map[String, AnyRef], parserContext: ParserContext): Mapper.Builder[_, _] =
      new Builder(name)
  }

  class Builder(name: String)
      extends FieldMapper.Builder[ElastiKnnVectorFieldMapper.Builder, ElastiKnnVectorFieldMapper](name, fieldType, fieldType) {
    override def build(context: Mapper.BuilderContext): ElastiKnnVectorFieldMapper = {
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
      throw new UnsupportedOperationException(s"Field [${name()}] of type [${typeName()}] doesn't support queries")

    override def existsQuery(context: QueryShardContext): Query =
      new DocValuesFieldExistsQuery(name())

    override def fielddataBuilder(indexName: String): fielddata.IndexFieldData.Builder =
      FieldData.Builder
  }

  object FieldData {
    object Builder extends fielddata.IndexFieldData.Builder {
      override def build(indexSettings: IndexSettings,
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

  class AtomicFieldData(reader: LeafReader, field: String) extends fielddata.AtomicFieldData {

    private lazy val bdv: BinaryDocValues = DocValues.getBinary(reader, field)

    final def getElastiKnnVector(docId: Int): Try[ElastiKnnVector] =
      if (bdv.advanceExact(docId))
        Success(ElastiKnnVector.parseBase64(bdv.binaryValue.utf8ToString))
      else Failure(new IllegalStateException(s"Couldn't parse a valid ElastiKnnVector for document id: $docId"))

    override def getScriptValues: fielddata.ScriptDocValues[_] =
      new ElastiKnnVectorScriptDocValues(bdv)

    override def getBytesValues: fielddata.SortedBinaryDocValues =
      throw new UnsupportedOperationException("String representation of doc values for elastiknn_vector fields is not supported")

    override def ramBytesUsed(): Long = 0L

    override def close(): Unit = ()
  }

}

class ElastiKnnVectorScriptDocValues(bdv: BinaryDocValues) extends fielddata.ScriptDocValues[Any] {

  private var stored: Option[ElastiKnnVector.Vector] = None
  private var storedGet: Int => Any = identity[Int]
  private var storedSize: Int = 0

  private[elastiknn] def getSparseBoolVector: SparseBoolVector = stored match {
    case Some(ElastiKnnVector.Vector.SparseBoolVector(sbv)) =>
      sbv
    case Some(_) => throw new IllegalStateException(s"Expected a ${SparseBoolVector.getClass.getName} but got $stored")
    case None    => throw new IllegalStateException("Vector hasn't been initialized")
  }

  private[elastiknn] def getFloatVector: FloatVector = stored match {
    case Some(ElastiKnnVector.Vector.FloatVector(fv)) =>
      fv
    case Some(_) => throw new IllegalStateException(s"Expected a ${FloatVector.getClass.getName} but got $stored")
    case None    => throw new IllegalStateException("Vector hasn't been initialized")
  }

  // TODO: deduplicate this advanceExact and parsing logic with the logic in AtomicFieldData above.
  override def setNextDocId(docId: Int): Unit =
    if (bdv.advanceExact(docId)) {
      // Spent a while figuring out if there's a way to decode this without converting to a string. Quite confused by
      // whatever is happening in Lucene that modifies the bytes when they're stored.
      stored = Some(ElastiKnnVector.parseBase64(bdv.binaryValue.utf8ToString).vector)
      stored match {
        case Some(ElastiKnnVector.Vector.FloatVector(v)) =>
          storedGet = v.values
          storedSize = v.values.length
        case Some(ElastiKnnVector.Vector.SparseBoolVector(v)) =>
          // Calling .get(-1) on a sparse bool vector will return the number of true indices.
          // TODO: is there another way to expose a custom script method?
          storedGet = (i: Int) => if (i == -1) v.totalIndices else v.trueIndices(i)
          storedSize = v.trueIndices.length
        case _ => throw new IllegalStateException(s"Couldn't parse a valid vector, found: $stored")
      }
    } else None

  override def get(i: Int): Any = storedGet(i)

  override def size(): Int = storedSize

}

class ElastiKnnVectorFieldMapper(simpleName: String,
                                 fieldType: MappedFieldType,
                                 defaultFieldType: MappedFieldType,
                                 indexSettings: Settings,
                                 multiFields: FieldMapper.MultiFields,
                                 copyTo: CopyTo)
    extends FieldMapper(simpleName, fieldType, defaultFieldType, indexSettings, multiFields, copyTo) {

  override def parse(context: ParseContext): Unit = {
    val json = context.parser.map.asJson(javaMapEncoder)
    val ekv = JsonFormat.fromJson[ElastiKnnVector](json) match {
      // Make sure that sparse vector indices are sorted. TODO: test this validation.
      case ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv)) =>
        ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv.sorted()))
      case other => other
    }
    // IMPORTANT: for some reason if you just use the regular protobuf bytes (not base64) then you get an error like:
    // "protocol message contained an invalid tag (zero)" when decoding. using base64 encoding fixes this.
    val field = new BinaryDocValuesField(fieldType.name, new BytesRef(ekv.toBase64))
    context.doc.addWithKey(fieldType.name, field)
  }

  override def parseCreateField(context: ParseContext, fields: util.List[IndexableField]): Unit =
    throw new IllegalStateException("parse is implemented directly")

  override def contentType(): String = {
    ElastiKnnVectorFieldMapper.CONTENT_TYPE
  }
}
