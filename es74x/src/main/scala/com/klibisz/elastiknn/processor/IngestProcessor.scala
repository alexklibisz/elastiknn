package com.klibisz.elastiknn.processor

import java.util

import com.klibisz.elastiknn._
import com.klibisz.elastiknn.models.ProcessedVector
import com.klibisz.elastiknn.utils.CirceUtils
import com.klibisz.elastiknn.utils.Utils._
import io.circe.Json
import io.circe.syntax._
import org.elasticsearch.common.xcontent.{DeprecationHandler, NamedXContentRegistry, XContentType}
import org.elasticsearch.ingest._
import scalapb_circe.JsonFormat

import scala.util.Try

class IngestProcessor private (tag: String, popts: ProcessorOptions) extends AbstractProcessor(tag) with CirceUtils {

  import popts._

  private def parseVector(doc: IngestDocument, field: String = fieldRaw): Try[ElastiKnnVector] =
    for {
      srcMap <- Try(doc.getFieldValue(field, classOf[util.Map[String, AnyRef]]))
      ekv <- ElastiKnnVector.from(srcMap)
      sorted = ekv match {
        case ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv)) =>
          ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv.sorted()))
        case _ => ekv
      }
    } yield sorted

  override def getType: String = IngestProcessor.TYPE

  private def setField(doc: IngestDocument, field: String, json: Json): Unit = {
    val reg = NamedXContentRegistry.EMPTY
    val dep = DeprecationHandler.THROW_UNSUPPORTED_OPERATION
    val parser = XContentType.JSON.xContent.createParser(reg, dep, json.noSpaces)
    doc.setFieldValue(field, parser.map())
  }

  private def process(doc: IngestDocument): Try[IngestDocument] =
    for {
      ekv <- parseVector(doc, fieldRaw)
      processed <- models.processVector(popts, ekv)
    } yield {
      modelOptions.fieldProc.foreach(fieldProc => setField(doc, fieldProc, processed.asJson))
      doc
    }

  /** This is the method that gets invoked when someone adds a document that uses an elastiknn pipeline. */
  override def execute(doc: IngestDocument): IngestDocument = process(doc).get

}

object IngestProcessor {

  lazy val TYPE: String = ELASTIKNN_NAME

  class Factory() extends Processor.Factory {

    /** This is the method that gets invoked when someone creates an elastiknn pipeline. */
    override def create(registry: util.Map[String, Processor.Factory], tag: String, config: util.Map[String, Object]): IngestProcessor = {
      val configJson = config.asJson
      val popts = JsonFormat.fromJson[ProcessorOptions](configJson)
      require(!popts.modelOptions.isEmpty, "model_options cannot be empty")
      popts.modelOptions.fieldProc.foreach(fieldProc => require(fieldProc.nonEmpty, "field_processed cannot be empty"))
      config.clear() // Need to do this otherwise ES thinks parsing didn't work!
      new IngestProcessor(tag, popts)
    }

  }

}
