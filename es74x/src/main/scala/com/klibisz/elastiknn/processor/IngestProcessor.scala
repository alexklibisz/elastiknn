package com.klibisz.elastiknn.processor

import java.util

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions
import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn._
import com.klibisz.elastiknn.models.{ExactModel, JaccardLshModel, VectorModel}
import com.klibisz.elastiknn.utils.CirceUtils._
import com.klibisz.elastiknn.utils.Implicits._
import io.circe.syntax._
import org.apache.logging.log4j.{LogManager, Logger}
import org.elasticsearch.client.node.NodeClient
import org.elasticsearch.common.xcontent.{DeprecationHandler, NamedXContentRegistry, XContent, XContentParser, XContentType}
import org.elasticsearch.ingest.{AbstractProcessor, IngestDocument, Processor}
import scalapb_circe.JsonFormat

import scala.util.{Failure, Success, Try}

class IngestProcessor private (tag: String, client: NodeClient, popts: ProcessorOptions) extends AbstractProcessor(tag) {

  import IngestProcessor._
  import popts._

  private def parseVector(doc: IngestDocument): Try[ElastiKnnVector] =
    (for {
      srcMap <- Try(doc.getFieldValue(fieldRaw, classOf[util.Map[String, AnyRef]]))
      ekv <- ElastiKnnVector.from(srcMap)
    } yield ekv).recoverWith {
      case ex => Failure(ParseVectorException(Some(s"Failed to parse ${ElastiKnnVector.scalaDescriptor.name} from $fieldRaw: $ex")))
    }

  private def parseFloatVector(doc: IngestDocument): Try[FloatVector] =
    for {
      ekv <- parseVector(doc)
      ret <- ekv.vector match {
        case ElastiKnnVector.Vector.FloatVector(fv) =>
          if (fv.values.length == popts.dimension) Success(fv) else Failure(VectorDimensionException(fv.values.length, popts.dimension))
        case v => Failure(illArgEx(s"Expected vector of floats but got: $v"))
      }
    } yield ret

  private def parseBoolVector(doc: IngestDocument): Try[SparseBoolVector] =
    for {
      ekv <- parseVector(doc)
      ret <- ekv.vector match {
        case ElastiKnnVector.Vector.SparseBoolVector(sbv) =>
          if (sbv.totalIndices == popts.dimension) Success(sbv) else Failure(VectorDimensionException(sbv.totalIndices, popts.dimension))
        case v => Failure(illArgEx(s"Expected vector of booleans but got: $v"))
      }
    } yield ret

  override def getType: String = IngestProcessor.TYPE

  /** This is the method that gets invoked when someone adds a document that uses an elastiknn pipeline. */
  override def execute(doc: IngestDocument): IngestDocument = {

    // Check if the raw vector is present.
    require(doc.hasField(fieldRaw), s"$TYPE expected to find vector at $fieldRaw")

    // Attempt to parse and process vector.
    val docTry: Try[IngestDocument] = for {
      vecRaw <- parseVector(doc)
      vecProc <- VectorModel.toJson(popts, vecRaw)
    } yield {
      // If the model options prescribe a processed field, set it here.
      modelOptions.fieldProc.foreach { fieldProc =>
        doc.setFieldValue(fieldProc,
                          XContentType.JSON.xContent
                            .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, vecProc.noSpaces))
      }
      doc // Return the doc, which may or may not be modified.
    }

    docTry.get
  }

}

object IngestProcessor {

  lazy val TYPE: String = ELASTIKNN_NAME

  private val modelCache: LoadingCache[ModelOptions, VectorModel[_]] =
    CacheBuilder.newBuilder.softValues.build(new CacheLoader[ModelOptions, VectorModel[_]] {
      override def load(key: ModelOptions): VectorModel[_] = key match {
        case ModelOptions.Exact(opts)   => new ExactModel(opts)
        case ModelOptions.Jaccard(opts) => new JaccardLshModel(opts)
        case ModelOptions.Empty         => new ExactModel(ExactModelOptions.defaultInstance)
      }
    })

  class Factory(client: NodeClient) extends Processor.Factory {

    private val logger: Logger = LogManager.getLogger(getClass)

    /** This is the method that gets invoked when someone creates an elastiknn pipeline. */
    override def create(registry: util.Map[String, Processor.Factory], tag: String, config: util.Map[String, Object]): IngestProcessor = {
      val configJson = config.asJson
      val popts = JsonFormat.fromJson[ProcessorOptions](configJson)
      require(!popts.modelOptions.isEmpty, "Model options are not defined")
      config.clear() // Need to do this otherwise ES thinks parsing didn't work!
      new IngestProcessor(tag, client, popts)
    }

  }

}
