package com.klibisz.elastiknn.mapper

import java.util

import com.klibisz.elastiknn.ElastiknnException.ElastiknnUnsupportedOperationException
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import com.klibisz.elastiknn.api.{ElasticsearchCodec, JavaJsonMap, Mapping, Vec}
import com.klibisz.elastiknn.query.{ExactQuery, HashingQuery, SparseIndexedQuery}
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.models.Cache
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import org.apache.lucene.index.{IndexOptions, IndexableField, Term}
import org.apache.lucene.search.similarities.BooleanSimilarity
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Query, TermQuery}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.lucene.Lucene
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder}
import org.elasticsearch.index.mapper.Mapper.TypeParser
import org.elasticsearch.index.mapper._
import org.elasticsearch.index.query.QueryShardContext
import org.elasticsearch.index.similarity.SimilarityProvider

import scala.util.{Failure, Try}

object VectorMapper {
  val sparseBoolVector: VectorMapper[Vec.SparseBool] =
    new VectorMapper[Vec.SparseBool] {
      override val CONTENT_TYPE: String = s"${ELASTIKNN_NAME}_sparse_bool_vector"
      override def checkAndCreateFields(fieldType: FieldType, vec: Vec.SparseBool): Try[Seq[IndexableField]] =
        if (fieldType.mapping.dims != vec.totalIndices)
          Failure(ElastiknnException.vectorDimensions(vec.totalIndices, fieldType.mapping.dims))
        else {
          val sorted = vec.sorted() // Sort for faster intersections on the query side.
          fieldType.mapping match {
            case Mapping.SparseBool(_)    => Try(ExactQuery.index(fieldType.fieldName, sorted))
            case Mapping.SparseIndexed(_) => Try(SparseIndexedQuery.index(fieldType.fieldName, fieldType, sorted))
            case m: Mapping.JaccardLsh =>
              Try(HashingQuery.index(fieldType.fieldName, fieldType, sorted, Cache(m).hash(vec.trueIndices, vec.totalIndices)))
            case m: Mapping.HammingLsh =>
              Try(HashingQuery.index(fieldType.fieldName, fieldType, sorted, Cache(m).hash(vec.trueIndices, vec.totalIndices)))
            case _ => Failure(incompatible(fieldType.mapping, vec))
          }
        }
    }
  val denseFloatVector: VectorMapper[Vec.DenseFloat] =
    new VectorMapper[Vec.DenseFloat] {
      override val CONTENT_TYPE: String = s"${ELASTIKNN_NAME}_dense_float_vector"
      override def checkAndCreateFields(fieldType: FieldType, vec: Vec.DenseFloat): Try[Seq[IndexableField]] =
        if (fieldType.mapping.dims != vec.values.length)
          Failure(ElastiknnException.vectorDimensions(vec.values.length, fieldType.mapping.dims))
        else
          fieldType.mapping match {
            case Mapping.DenseFloat(_)     => Try(ExactQuery.index(fieldType.fieldName, vec))
            case m: Mapping.AngularLsh     => Try(HashingQuery.index(fieldType.fieldName, fieldType, vec, Cache(m).hash(vec.values)))
            case m: Mapping.L2Lsh          => Try(HashingQuery.index(fieldType.fieldName, fieldType, vec, Cache(m).hash(vec.values)))
            case m: Mapping.PermutationLsh => Try(HashingQuery.index(fieldType.fieldName, fieldType, vec, Cache(m).hash(vec.values)))
            case _                         => Failure(incompatible(fieldType.mapping, vec))
          }
    }

  private def incompatible(m: Mapping, v: Vec): Exception = new IllegalArgumentException(
    s"Mapping [${nospaces(m)}] is not compatible with vector [${nospaces(v)}]"
  )

  case class FieldType(override val typeName: String, fieldName: String, mapping: Mapping) extends MappedFieldType {

    // We generally only care about the presence or absence of terms, not their counts or anything fancier.
    setSimilarity(new SimilarityProvider("boolean", new BooleanSimilarity))
    setOmitNorms(true)
    setBoost(1f)
    setTokenized(false)
    setIndexOptions(IndexOptions.DOCS_AND_FREQS)
    setStoreTermVectors(false)

    // TODO: are these necessary?
    setIndexAnalyzer(Lucene.KEYWORD_ANALYZER)
    setSearchAnalyzer(Lucene.KEYWORD_ANALYZER)

    override def name(): String = fieldName
    override def clone(): FieldType = new FieldType(typeName, fieldName, mapping)
    override def termQuery(value: Any, context: QueryShardContext): Query = value match {
      case b: BytesRef => new TermQuery(new Term(name(), b))
      case _ =>
        throw new ElastiknnUnsupportedOperationException(
          s"Field [${name()}] of type [${typeName}] doesn't support term queries with value of type [${value.getClass}]")
    }

    override def existsQuery(context: QueryShardContext): Query = new DocValuesFieldExistsQuery(name())
  }

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

}

abstract class VectorMapper[V <: Vec: ElasticsearchCodec] { self =>

  val CONTENT_TYPE: String
  def checkAndCreateFields(fieldType: VectorMapper.FieldType, vec: V): Try[Seq[IndexableField]]

  import com.klibisz.elastiknn.utils.CirceUtils.javaMapEncoder

  class TypeParser extends Mapper.TypeParser {
    override def parse(name: String, node: JavaJsonMap, parserContext: TypeParser.ParserContext): Mapper.Builder[_, _] = {
      val mapping: Mapping = ElasticsearchCodec.decodeJsonGet[Mapping](node.asJson)
      val builder: FieldMapper.Builder[Builder, FieldMapper] = Builder(name, mapping)
      TypeParsers.parseField(builder, name, node, parserContext)
      node.clear()
      builder
    }
  }

  private class Builder(fieldName: String, mapping: Mapping, fieldType: VectorMapper.FieldType, defaultFieldType: VectorMapper.FieldType)
      extends FieldMapper.Builder[Builder, FieldMapper](fieldName, fieldType, defaultFieldType) {
    override def build(context: Mapper.BuilderContext): FieldMapper = {
      super.setupFieldType(context)
      // Re-define the fieldtype using the context to define the fieldName to support nested fields.
      val fullContextFieldType = fieldType.copy(fieldName = context.path.pathAsText(fieldName))
      new FieldMapper(fieldName,
                      fullContextFieldType,
                      defaultFieldType,
                      context.indexSettings(),
                      multiFieldsBuilder.build(this, context),
                      copyTo) {
        override def parse(context: ParseContext): Unit = {
          val doc: ParseContext.Document = context.doc()
          val json: Json = context.parser.map.asJson
          val vec = ElasticsearchCodec.decodeJsonGet[V](json)
          val fields = self.checkAndCreateFields(fullContextFieldType, vec).get
          fields.foreach(doc.add)
        }
        override def parseCreateField(context: ParseContext, fields: util.List[IndexableField]): Unit =
          throw new ElastiknnUnsupportedOperationException("parse() is implemented directly")
        override def contentType(): String = CONTENT_TYPE
        override def doXContentBody(builder: XContentBuilder, includeDefaults: Boolean, params: ToXContent.Params): Unit = {
          super.doXContentBody(builder, includeDefaults, params)
          ElasticsearchCodec
            .encode(mapping)
            .asObject
            .map(_.toIterable.filter(_._1 != "type"))
            .map(JsonObject.fromIterable)
            .map(Json.fromJsonObject)
            .foreach(VectorMapper.populateXContent(_, builder))

        }
      }
    }
  }

  private object Builder {
    def apply(fieldName: String, mapping: Mapping): FieldMapper.Builder[Builder, FieldMapper] = {
      val fieldType = new VectorMapper.FieldType(CONTENT_TYPE, fieldName, mapping)
      new Builder(fieldName, mapping, fieldType, fieldType)
    }
  }

}
