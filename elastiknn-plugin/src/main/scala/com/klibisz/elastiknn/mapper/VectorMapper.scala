package com.klibisz.elastiknn.mapper

import com.klibisz.elastiknn.ElastiknnException.ElastiknnUnsupportedOperationException
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.lucene.HashFieldType
import com.klibisz.elastiknn.models.ModelCache
import com.klibisz.elastiknn.query.{ExactQuery, HashingQuery}
import org.apache.lucene.document.{FieldType => LuceneFieldType}
import org.apache.lucene.index.{IndexableField, Term}
import org.apache.lucene.search.{Query, TermQuery}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.index.mapper.{Mapping => _, _}
import org.elasticsearch.index.query.SearchExecutionContext
import org.elasticsearch.search.fetch.StoredFieldsSpec
import org.elasticsearch.search.lookup.Source
import org.elasticsearch.xcontent.{ToXContent, XContentBuilder}

import java.util
import java.util.Collections
import scala.util.{Failure, Try}

object VectorMapper {

  final class SparseBoolVectorMapper(modelCache: ModelCache) extends VectorMapper[Vec.SparseBool] {
    override val CONTENT_TYPE: String = SparseBoolVectorMapper.CONTENT_TYPE
    override def checkAndCreateFields(mapping: Mapping, field: String, vec: Vec.SparseBool): Try[Seq[IndexableField]] =
      if (mapping.dims != vec.totalIndices)
        Failure(ElastiknnException.vectorDimensions(vec.totalIndices, mapping.dims))
      else {
        val sorted = vec.sorted() // Sort for faster intersections on the query side.
        mapping match {
          case Mapping.SparseBool(_) => Try(ExactQuery.index(field, sorted))
          case m: Mapping.JaccardLsh =>
            Try(HashingQuery.index(field, luceneFieldType, sorted, modelCache(m).hash(vec.trueIndices, vec.totalIndices)))
          case m: Mapping.HammingLsh =>
            Try(HashingQuery.index(field, luceneFieldType, sorted, modelCache(m).hash(vec.trueIndices, vec.totalIndices)))
          case _ => Failure(incompatible(mapping, vec))
        }
      }
  }

  object SparseBoolVectorMapper {
    val CONTENT_TYPE: String = s"${ELASTIKNN_NAME}_sparse_bool_vector"
  }

  final class DenseFloatVectorMapper(modelCache: ModelCache) extends VectorMapper[Vec.DenseFloat] {
    override val CONTENT_TYPE: String = DenseFloatVectorMapper.CONTENT_TYPE
    override def checkAndCreateFields(mapping: Mapping, field: String, vec: Vec.DenseFloat): Try[Seq[IndexableField]] =
      if (mapping.dims != vec.values.length)
        Failure(ElastiknnException.vectorDimensions(vec.values.length, mapping.dims))
      else
        mapping match {
          case Mapping.DenseFloat(_)     => Try(ExactQuery.index(field, vec))
          case m: Mapping.CosineLsh      => Try(HashingQuery.index(field, luceneFieldType, vec, modelCache(m).hash(vec.values)))
          case m: Mapping.L2Lsh          => Try(HashingQuery.index(field, luceneFieldType, vec, modelCache(m).hash(vec.values)))
          case m: Mapping.PermutationLsh => Try(HashingQuery.index(field, luceneFieldType, vec, modelCache(m).hash(vec.values)))
          case _                         => Failure(incompatible(mapping, vec))
        }
  }

  object DenseFloatVectorMapper {
    val CONTENT_TYPE: String = s"${ELASTIKNN_NAME}_dense_float_vector"
  }

  private def incompatible(m: Mapping, v: Vec): Exception =
    new IllegalArgumentException(s"Mapping [$m] is not compatible with vector [$v]")

  // TODO: 7.9.x. Unsure if the constructor params passed to the superclass are correct.
  class FieldType(contentTypeName: String, fieldName: String, val mapping: Mapping)
      extends MappedFieldType(fieldName, true, true, true, TextSearchInfo.NONE, Collections.emptyMap()) {
    override def typeName(): String = contentTypeName
    override def clone(): FieldType = new FieldType(contentTypeName, fieldName, mapping)
    override def termQuery(value: Any, context: SearchExecutionContext): Query = {
      value match {
        case b: BytesRef => new TermQuery(new Term(name(), b))
        case _ =>
          val msg = s"Field [${name()}] of type [${typeName()}] doesn't support term queries with value of type [${value.getClass}]"
          throw new ElastiknnUnsupportedOperationException(msg)
      }
    }
    override def valueFetcher(context: SearchExecutionContext, format: String): ValueFetcher = {
      new ValueFetcher {
        override def fetchValues(source: Source, doc: Int, ignoredValues: util.List[AnyRef]): util.List[AnyRef] = util.List.of()
        override def storedFieldsSpec(): StoredFieldsSpec = StoredFieldsSpec.NO_REQUIREMENTS
      }
    }
  }
}

abstract class VectorMapper[V <: Vec: XContentCodec.Decoder] { self =>

  def CONTENT_TYPE: String
  def checkAndCreateFields(mapping: Mapping, field: String, vec: V): Try[Seq[IndexableField]]

  final val luceneFieldType: LuceneFieldType = HashFieldType.HASH_FIELD_TYPE

  object TypeParser extends Mapper.TypeParser {
    override def parse(name: String, node: java.util.Map[String, Object], parserContext: MappingParserContext): Mapper.Builder = {
      val mapping = XContentCodec.decodeUnsafeFromMap[Mapping](node)
      val builder: Builder = new Builder(name, mapping)
      node.clear()
      builder
    }
  }

  private final class Builder(field: String, mapping: Mapping) extends FieldMapper.Builder(field) {

    override def build(context: MapperBuilderContext): FieldMapper =
      new FieldMapper(
        field,
        new VectorMapper.FieldType(CONTENT_TYPE, context.buildFullName(name), mapping),
        multiFieldsBuilder.build(this, context),
        copyTo
      ) {
        override def parsesArrayValue(): Boolean = true

        override def parse(context: DocumentParserContext): Unit = {
          val doc = context.doc()
          val parser = context.parser()
          val vec: V = XContentCodec.decodeUnsafe[V](parser)
          val fields = checkAndCreateFields(mapping, name, vec).get
          fields.foreach(doc.add)
        }

        override def parseCreateField(context: DocumentParserContext): Unit =
          throw new IllegalStateException("parse() is implemented directly")

        override def contentType(): String = CONTENT_TYPE

        override def doXContentBody(builder: XContentBuilder, params: ToXContent.Params): Unit = {
          super.doXContentBody(builder, params)
          // Note: encoding method should NOT write the "type" property to the builder.
          // It has presumably already been written, and writing it again causes a tricky exception.
          XContentCodec.Encoder.mapping.encodeElastiknnObject(mapping, builder)
        }

        override def getMergeBuilder: FieldMapper.Builder = new Builder(simpleName(), mapping)
      }

    override def getParameters: Array[FieldMapper.Parameter[_]] = Array.empty
  }
}
