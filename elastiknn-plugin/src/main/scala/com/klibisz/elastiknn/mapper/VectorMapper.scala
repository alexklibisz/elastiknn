package com.klibisz.elastiknn.mapper

import com.klibisz.elastiknn.ElastiknnException.ElastiknnUnsupportedOperationException
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.api.ElasticsearchCodec._
import com.klibisz.elastiknn.api.{ElasticsearchCodec, JavaJsonMap, Mapping, Vec}
import com.klibisz.elastiknn.models.Cache
import com.klibisz.elastiknn.query.{ExactQuery, HashingQuery}
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import org.apache.lucene.document.{FieldType => LuceneFieldType}
import org.apache.lucene.index.{IndexOptions, IndexableField, Term}
import org.apache.lucene.search.{Query, TermQuery}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.xcontent.XContentParser.Token
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder}
import org.elasticsearch.index.mapper._
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
    new IllegalArgumentException(
      s"Mapping [${nospaces(m)}] is not compatible with vector [${nospaces(v)}]"
    )

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

abstract class VectorMapper[V <: Vec: ElasticsearchCodec] { self =>

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

  import com.klibisz.elastiknn.utils.CirceUtils._

  class TypeParser extends Mapper.TypeParser {
    override def parse(name: String, node: JavaJsonMap, parserContext: MappingParserContext): Mapper.Builder = {
      val mapping: Mapping = ElasticsearchCodec.decodeJsonGet[Mapping](node.asJson)
      val builder: Builder = new Builder(name, mapping)
      // TypeParsers.parseField(builder, name, node, parserContext)
      node.clear()
      builder
    }
  }

  private final class Builder(field: String, mapping: Mapping) extends FieldMapper.Builder(field) {

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
        }
        else if (json.isArray) json.asArray.foreach(_.foreach(populate))
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
          val json = {
            // The XContentParser's current token tells us how to convert its contents into Circe Json which can be
            // neatly parsed into a vector. If the parser is pointing at a new object, convert it to a map and then
            // conver to Json. If it's pointing at an array, convert to a list, then to Json. Otherwise it's not
            // parseable so return a Null Json.
            if (parser.currentToken() == Token.START_OBJECT) context.parser.map.asJson
            else if (parser.currentToken() == Token.START_ARRAY) context.parser.list.asJson
            else Json.Null
          }
          val vec = ElasticsearchCodec.decodeJsonGet[V](json)
          val fields = checkAndCreateFields(mapping, name, vec).get
          fields.foreach(doc.add)
        }

        override def parseCreateField(context: ParseContext): Unit =
          throw new IllegalStateException("parse() is implemented directly")

        override def contentType(): String = CONTENT_TYPE

        override def doXContentBody(builder: XContentBuilder, params: ToXContent.Params): Unit = {
          super.doXContentBody(builder, params)
          ElasticsearchCodec
            .encode(mapping)
            .asObject
            .map(_.toIterable.filter(_._1 != "type"))
            .map(JsonObject.fromIterable)
            .map(Json.fromJsonObject)
            .foreach(populateXContent(_, builder))

        }

        override def getMergeBuilder: FieldMapper.Builder = new Builder(simpleName(), mapping)
      }
    }

    override def getParameters: util.List[FieldMapper.Parameter[_]] = util.List.of()
  }

}
