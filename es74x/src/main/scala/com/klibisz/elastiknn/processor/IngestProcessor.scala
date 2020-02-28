package com.klibisz.elastiknn.processor

import java.util

import com.klibisz.elastiknn._
import com.klibisz.elastiknn.models.VectorHashingModel
import com.klibisz.elastiknn.utils.CirceUtils
import com.klibisz.elastiknn.utils.Utils._
import io.circe.syntax._
import org.elasticsearch.ingest.{AbstractProcessor, IngestDocument, Processor}
import scalapb_circe.JsonFormat

import scala.util.Try

class IngestProcessor private (tag: String, popts: ProcessorOptions) extends AbstractProcessor(tag) with CirceUtils {

  import popts._

  private def parseVector(doc: IngestDocument, field: String = fieldRaw): Try[ElastiKnnVector] =
    for {
      srcMap <- Try(doc.getFieldValue(field, classOf[util.Map[String, AnyRef]]))
      ekv <- ElastiKnnVector.from(srcMap)
    } yield ekv

  override def getType: String = IngestProcessor.TYPE

  private def process(doc: IngestDocument, fieldPrefix: String = ""): Try[IngestDocument] =
    for {
      raw <- parseVector(doc, s"$fieldPrefix$fieldRaw")
      hashed: String <- VectorHashingModel.hash(popts, raw)
    } yield {
      modelOptions.fieldProc.foreach(fieldProc => doc.setFieldValue(s"$fieldPrefix$fieldProc", hashed))
      doc
    }

  /** This is the method that gets invoked when someone adds a document that uses an elastiknn pipeline. */
  override def execute(doc: IngestDocument): IngestDocument = {
    // The official python client puts bulk-indexed docs under a `doc` key. elastic4s doesn't seem to do this.
    // Still, it's safest to try both no prefix and the `doc.` prefix.
    process(doc).get
    //    process(doc).orElse(process(doc, "doc.")).get
  }

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
