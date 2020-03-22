package com.klibisz.elastiknn.mapper

import java.io.Reader
import java.util

import com.klibisz.elastiknn.ELASTIKNN_NAME
import com.klibisz.elastiknn.models.{ExactModelOptions, JaccardIndexedModelOptions, JaccardLshModelOptions, SparseBoolVectorModelOptions}
import org.apache.lucene.analysis.{Analyzer, TokenStream}
import org.apache.lucene.document.StoredField
import org.apache.lucene.index.{IndexableField, IndexableFieldType}
import org.apache.lucene.search.{DocValuesFieldExistsQuery, Query}
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.ParsingException
import org.elasticsearch.index.mapper.{FieldMapper, MappedFieldType, Mapper, ParseContext}
import org.elasticsearch.index.mapper.Mapper.TypeParser
import org.elasticsearch.index.query.QueryShardContext
import io.circe.generic.auto._
import io.circe.syntax._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object SparseBoolVectorMapper {

  case class ModelOptions(`type`: String, bands: Option[Int], rows: Option[Int]) {
//    def refined: Try[SparseBoolVectorModelOptions] = this match {
//      case ModelOptions()
//      case ModelOptions("jaccard_indexed", _, _) =>
//        Success(JaccardIndexedModelOptions)
//      case ModelOptions("jaccard_lsh", Some(bands), Some(rows)) =>
//        Success(JaccardLshModelOptions(bands, rows))
//
//    }
  }
  case class Mapping(`type`: String, dims: Int, model_options: Option[ModelOptions])

  val CONTENT_TYPE = s"${ELASTIKNN_NAME}_sparse_bool_vector"

  val fieldType = new this.FieldType

  class TypeParser extends Mapper.TypeParser {
    override def parse(name: String, node: util.Map[String, AnyRef], parserContext: TypeParser.ParserContext): Mapper.Builder[_, _] = {

      import io.circe.syntax._
      import com.klibisz.elastiknn.utils.CirceUtils.javaMapEncoder

      val j = node.asJson(javaMapEncoder)

//      val scnode = node.asScala
//      scnode.getOrElse("dims", throw new IllegalArgumentException("Expected "))
//      val dims = parseOrThrow(node.get("dims").asInstanceOf[Int], "Expected integer called dims")
//      val mopts =
//        parseOrThrow(node.get("model_options").asInstanceOf[java.util.Map[String, AnyRef]], "Expected object called model_options")
//      val moptsType =
      node.clear()
      new Builder(name, 0, ExactModelOptions)
    }
  }

  class Builder(name: String, dims: Int, modelOpts: SparseBoolVectorModelOptions)
      extends FieldMapper.Builder[SparseBoolVectorMapper.Builder, FieldMapper](name, fieldType, fieldType) {
    override def build(context: Mapper.BuilderContext): FieldMapper = {
      super.setupFieldType(context)
      new FieldMapper(name, fieldType, defaultFieldType, context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo) {
        override def parse(context: ParseContext): Unit = {
          ???
//          context.doc().add(new IndexableField {
//            override def name(): String = ???
//
//            override def fieldType(): IndexableFieldType = ???
//
//            override def tokenStream(analyzer: Analyzer, reuse: TokenStream): TokenStream = ???
//
//            override def binaryValue(): BytesRef = ???
//
//            override def stringValue(): String = ???
//
//            override def readerValue(): Reader = ???
//
//            override def numericValue(): Number = ???
//          })
        }
        override def parseCreateField(context: ParseContext, fields: util.List[IndexableField]): Unit =
          throw new IllegalStateException("parse() is implemented directly")
        override def contentType(): String = CONTENT_TYPE
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
