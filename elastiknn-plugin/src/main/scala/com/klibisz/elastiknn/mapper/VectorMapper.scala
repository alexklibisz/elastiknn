package com.klibisz.elastiknn.mapper

import com.klibisz.elastiknn.ElastiknnException.ElastiknnUnsupportedOperationException
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.models.Cache
import com.klibisz.elastiknn.query.{ExactQuery, HashingQuery}
import org.apache.lucene.document.{FieldType => LuceneFieldType}
import org.apache.lucene.index.{IndexOptions, IndexableField, Term}
import org.apache.lucene.search.{Query, TermQuery}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder}
import org.elasticsearch.index.mapper.{Mapping => _, _}
import org.elasticsearch.index.query.SearchExecutionContext
import org.elasticsearch.search.lookup.SourceLookup

import java.util
import java.util.Collections
import scala.util.{Failure, Try}

object VectorMapper {

  val sparseBoolVector: VectorMapper[Vec.SparseBool] =
    new VectorMapper[Vec.SparseBool] {
      override val CONTENT_TYPE: String = s"${ELASTIKNN_NAME}_sparse_bool_vector"
      override def checkAndCreateFields(mapping: Mapping, field: String, vec: Vec.SparseBool): Try[Seq[IndexableField]] =
        if (mapping.dims != vec.totalIndices)
          Failure(ElastiknnException.vectorDimensions(vec.totalIndices, mapping.dims))
        else {
          val sorted = vec.sorted() // Sort for faster intersections on the query side.
          mapping match {
            case Mapping.SparseBool(_) => Try(ExactQuery.index(field, sorted))
            case m: Mapping.JaccardLsh =>
              Try(HashingQuery.index(field, luceneFieldType, sorted, Cache(m).hash(vec.trueIndices, vec.totalIndices)))
            case m: Mapping.HammingLsh =>
              Try(HashingQuery.index(field, luceneFieldType, sorted, Cache(m).hash(vec.trueIndices, vec.totalIndices)))
            case _ => Failure(incompatible(mapping, vec))
          }
        }
    }
  val denseFloatVector: VectorMapper[Vec.DenseFloat] =
    new VectorMapper[Vec.DenseFloat] {
      override val CONTENT_TYPE: String = s"${ELASTIKNN_NAME}_dense_float_vector"
      override def checkAndCreateFields(mapping: Mapping, field: String, vec: Vec.DenseFloat): Try[Seq[IndexableField]] =
        if (mapping.dims != vec.values.length)
          Failure(ElastiknnException.vectorDimensions(vec.values.length, mapping.dims))
        else
          mapping match {
            case Mapping.DenseFloat(_)     => Try(ExactQuery.index(field, vec))
            case m: Mapping.CosineLsh      => Try(HashingQuery.index(field, luceneFieldType, vec, Cache(m).hash(vec.values)))
            case m: Mapping.L2Lsh          => Try(HashingQuery.index(field, luceneFieldType, vec, Cache(m).hash(vec.values)))
            case m: Mapping.PermutationLsh => Try(HashingQuery.index(field, luceneFieldType, vec, Cache(m).hash(vec.values)))
            case _                         => Failure(incompatible(mapping, vec))
          }
    }

  private def incompatible(m: Mapping, v: Vec): Exception =
    new IllegalArgumentException(s"Mapping [$m] is not compatible with vector [$v]")

  // TODO: 7.9.x. Unsure if the constructor params passed to the superclass are correct.
  class FieldType(typeName: String, fieldName: String, val mapping: Mapping)
      extends MappedFieldType(fieldName, true, true, true, TextSearchInfo.NONE, Collections.emptyMap()) {
    override def typeName(): String = typeName
    override def clone(): FieldType = new FieldType(typeName, fieldName, mapping)
    override def termQuery(value: Any, context: SearchExecutionContext): Query = {
      value match {
        case b: BytesRef => new TermQuery(new Term(name(), b))
        case _ =>
          val msg = s"Field [${name()}] of type [${typeName()}] doesn't support term queries with value of type [${value.getClass}]"
          throw new ElastiknnUnsupportedOperationException(msg)
      }
    }
    override def valueFetcher(context: SearchExecutionContext, format: String): ValueFetcher = {
      // TODO: figure out what this is supposed to return. Also see issue #250.
      (_: SourceLookup) => util.List.of()
    }
  }
}

abstract class VectorMapper[V <: Vec: XContentCodec.Decoder: XContentCodec.Encoder] { self =>

  def CONTENT_TYPE: String
  def checkAndCreateFields(mapping: Mapping, field: String, vec: V): Try[Seq[IndexableField]]

  final def luceneFieldType: LuceneFieldType = {
    // TODO 7.9.2: is there a way (or a need) to call setSimilarity, setIndexAnalyzer, setSearchAnalyzer?
    val ft = new LuceneFieldType()
    ft.setTokenized(false)
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
    ft.setOmitNorms(true)
    ft.freeze()
    ft
  }

  class TypeParser extends Mapper.TypeParser {
    override def parse(name: String, node: java.util.Map[String, Object], parserContext: MappingParserContext): Mapper.Builder = {
      val mapping = XContentCodec.decodeUnsafeFromMap[Mapping](node)
      val builder: Builder = new Builder(name, mapping)
      node.clear()
      builder
    }
  }

  private final class Builder(field: String, mapping: Mapping) extends FieldMapper.Builder(field) {

    override def build(contentPath: ContentPath): FieldMapper = {
      new FieldMapper(
        field,
        new VectorMapper.FieldType(CONTENT_TYPE, contentPath.pathAsText(name), mapping),
        multiFieldsBuilder.build(this, contentPath),
        copyTo.build()
      ) {
        override def parsesArrayValue(): Boolean = true

        override def parse(context: ParseContext): Unit = {
          val doc = context.doc()
          val parser = context.parser()
          val vec: V = XContentCodec.decodeUnsafe[V](parser)
          val fields = checkAndCreateFields(mapping, name, vec).get
          fields.foreach(doc.add)
        }

        override def parseCreateField(context: ParseContext): Unit =
          throw new IllegalStateException("parse() is implemented directly")

        override def contentType(): String = CONTENT_TYPE

        override def doXContentBody(builder: XContentBuilder, params: ToXContent.Params): Unit = {
          super.doXContentBody(builder, params)
          // Note: encoding method should NOT write the "type" property to the builder.
          // It has presumably already been written, and writing it again causes a tricky exception.
          XContentCodec.Encoder.mapping.encodeUnsafeInner(mapping, builder)
        }

        override def getMergeBuilder: FieldMapper.Builder = new Builder(simpleName(), mapping)
      }
    }

    override def getParameters: util.List[FieldMapper.Parameter[_]] = util.List.of()
  }

}
