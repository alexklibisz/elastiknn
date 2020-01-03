package com.klibisz.elastiknn.processor

import java.util

import com.klibisz.elastiknn._
import com.klibisz.elastiknn.models.VectorModel
import com.klibisz.elastiknn.utils.CirceUtils._
import com.klibisz.elastiknn.utils.Implicits._
import io.circe.Json
import io.circe.syntax._
import org.apache.logging.log4j.{LogManager, Logger}
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.xcontent.{DeprecationHandler, NamedXContentRegistry, XContentType}
import org.elasticsearch.ingest.{AbstractProcessor, IngestDocument, Processor}
import scalapb_circe.JsonFormat

import scala.util.{Failure, Try}

class IngestProcessor private (tag: String, client: NodeClient, popts: ProcessorOptions) extends AbstractProcessor(tag) {

  import popts._

  private def parseVector(doc: IngestDocument, field: String = fieldRaw): Try[ElastiKnnVector] =
    (for {
      srcMap <- Try(doc.getFieldValue(field, classOf[util.Map[String, AnyRef]]))
      ekv <- ElastiKnnVector.from(srcMap)
    } yield ekv).recoverWith {
      case ex => Failure(ParseVectorException(s"Failed to parse ${ElastiKnnVector.scalaDescriptor.name} from field: $field", Some(ex)))
    }

  private def setField(doc: IngestDocument, field: String, json: Json): Unit = {
    val reg = NamedXContentRegistry.EMPTY
    val dep = DeprecationHandler.THROW_UNSUPPORTED_OPERATION
    val parser = XContentType.JSON.xContent.createParser(reg, dep, json.noSpaces)
    doc.setFieldValue(field, parser.map())
  }

  override def getType: String = IngestProcessor.TYPE

  private def process(doc: IngestDocument, fieldPrefix: String): Try[IngestDocument] =
    for {
      raw <- parseVector(doc, s"$fieldPrefix$fieldRaw")
      proc <- VectorModel.toJson(popts, raw)
    } yield {
      modelOptions.fieldProc.foreach(fieldProc => setField(doc, s"$fieldPrefix$fieldProc", proc))
      doc
    }

  /** This is the method that gets invoked when someone adds a document that uses an elastiknn pipeline. */
  override def execute(doc: IngestDocument): IngestDocument = {
    // The official python client puts bulk-indexed docs under a `doc` key. elastic4s doesn't seem to do this.
    // Still, it's safest to try both no prefix and the `doc.` prefix.
    process(doc, "").orElse(process(doc, "doc.")).get
  }

}

object IngestProcessor {

  lazy val TYPE: String = ELASTIKNN_NAME

  class Factory(client: NodeClient) extends Processor.Factory {

    private val logger: Logger = LogManager.getLogger(getClass)

    /** This is the method that gets invoked when someone creates an elastiknn pipeline. */
    override def create(registry: util.Map[String, Processor.Factory], tag: String, config: util.Map[String, Object]): IngestProcessor = {
      val configJson = config.asJson
      val popts = JsonFormat.fromJson[ProcessorOptions](configJson)
      require(!popts.modelOptions.isEmpty, "model_options cannot be empty")
      popts.modelOptions.fieldProc.foreach(fieldProc => require(fieldProc.nonEmpty, "field_processed cannot be empty"))
      config.clear() // Need to do this otherwise ES thinks parsing didn't work!
      new IngestProcessor(tag, client, popts)
    }

  }

}
