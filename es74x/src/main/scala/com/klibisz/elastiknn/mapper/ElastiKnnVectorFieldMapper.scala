package com.klibisz.elastiknn.mapper

import java.util

import com.klibisz.elastiknn._
import com.klibisz.elastiknn.utils.CirceUtils.javaMapEncoder
import com.klibisz.elastiknn.utils.Utils._
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

import scala.util.{Failure, Success, Try}

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
    override def parse(name: String, node: util.Map[String, AnyRef], parserContext: ParserContext): Mapper.Builder[_, _] = {
      new Builder(name)
    }
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

    override def termQuery(value: Any, context: QueryShardContext): Query = {
      // TODO: should we implement termQuery? See: https://github.com/elastic/elasticsearch/pull/44374/files#diff-3e8da1369743959b46f7e6fa0d63c1afR252
      throw new UnsupportedOperationException(s"Field [${name()}] of type [${typeName()}] doesn't support queries")
    }

    override def existsQuery(context: QueryShardContext): Query = new DocValuesFieldExistsQuery(name())

    override def fielddataBuilder(indexName: String): fielddata.IndexFieldData.Builder = FieldData.Builder
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
    override def load(context: LeafReaderContext): AtomicFieldData = new AtomicFieldData(context.reader(), fieldName)

    override def loadDirect(context: LeafReaderContext): AtomicFieldData = load(context)

    override def sortField(missingValue: Any,
                           sortMode: MultiValueMode,
                           nested: XFieldComparatorSource.Nested,
                           reverse: Boolean): SortField = throw new UnsupportedOperationException("Sorting is not supported")
  }

  class AtomicFieldData(reader: LeafReader, field: String) extends fielddata.AtomicFieldData {

    private lazy val bdv: BinaryDocValues = DocValues.getBinary(reader, field)

    final def getElastiKnnVector(docId: Int): Try[ElastiKnnVector] = parseFromBinaryDocValues(bdv, docId)

    override def getScriptValues: fielddata.ScriptDocValues[_] = new ScriptDocValues(bdv)

    override def getBytesValues: fielddata.SortedBinaryDocValues =
      throw new UnsupportedOperationException(s"String representation of doc values for $CONTENT_TYPE fields is not supported")

    override def ramBytesUsed(): Long = 0L

    override def close(): Unit = ()
  }

  def parseFromBinaryDocValues(binaryDocValues: BinaryDocValues, docId: Int): Try[ElastiKnnVector] = {
    if (binaryDocValues.advanceExact(docId)) {
      // Sometimes the bv.bytes array is longer than bv.length, so you have to call .take.
      // It seems like the same array gets re-used between calls to the same shard.
      val bv = binaryDocValues.binaryValue
      Try(ElastiKnnVector.parseFrom(bv.bytes.take(bv.length)))
    } else Failure(new IllegalStateException(s"Couldn't parse a valid $CONTENT_TYPE for document id: $docId"))
  }

}

class ScriptDocValues(bdv: BinaryDocValues) extends fielddata.ScriptDocValues[Any] {

  // These vars will get defined when the vector is parsed; they're different for float and sparse bool vectors.
  private var storedGet: Int => Any = identity[Int]
  private var storedSize: Int = 0

  override def setNextDocId(docId: Int): Unit =
    ElastiKnnVectorFieldMapper.parseFromBinaryDocValues(bdv, docId).map(_.vector) match {
      case Success(ElastiKnnVector.Vector.FloatVector(v)) =>
        storedGet = (i: Int) => v.values(i)
        storedSize = v.values.length
      case Success(ElastiKnnVector.Vector.SparseBoolVector(v)) =>
        // Bit of a quirk, but calling .get(-1) will return the total number of indices.
        storedGet = (i: Int) => if (i < 0) v.totalIndices else v.trueIndices(i)
        storedSize = v.trueIndices.length
      case Success(ElastiKnnVector.Vector.Empty) => throw new IllegalStateException(s"Parsed an empty vector for docId $docId")
      case Failure(t)                            => throw t
    }

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
    val bref = new BytesRef(ekv.toByteArray)
    context.doc.add(new BinaryDocValuesField(fieldType.name, bref))
  }

  override def parseCreateField(context: ParseContext, fields: util.List[IndexableField]): Unit =
    throw new IllegalStateException("parse is implemented directly")

  override def contentType(): String = ElastiKnnVectorFieldMapper.CONTENT_TYPE
}
