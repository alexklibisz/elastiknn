package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.ElastiKnnVector.Vector.{FloatVector, SparseBoolVector}
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions._
import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn._
import io.circe._
import io.circe.generic.semiauto.deriveEncoder

import scala.util._

trait VectorModel[R] {

  /**
    * Map a vector to a Try of some representation which can be converted Json.
    * @param v The vector
    * @param enc Encoder typeclass so the representation can be mapped to JSON.
    * @param dec Decoder typeclass so the representation can be parsed from JSON.
    * @return
    */
  def apply(v: ElastiKnnVector)(implicit enc: Encoder[R], dec: Decoder[R]): Try[R]
}

case class ExactRepresentation()
object ExactRepresentation {
  implicit def encoder: Encoder[ExactRepresentation] = deriveEncoder[ExactRepresentation]
}

class ExactModel(opts: ExactModelOptions) extends VectorModel[ExactRepresentation] {
  def apply(v: ElastiKnnVector)(implicit enc: Encoder[ExactRepresentation], dec: Decoder[ExactRepresentation]): Try[ExactRepresentation] =
    ???
}

object ExactModel {
  def apply(popts: ProcessorOptions, mopts: ExactModelOptions, vec: ElastiKnnVector): Try[Unit] = (mopts.similarity, vec) match {
    case (SIMILARITY_ANGULAR | SIMILARITY_L1 | SIMILARITY_L2, ElastiKnnVector(FloatVector(fv))) =>
      if (fv.values.length == popts.dimension) Success(()) else Failure(VectorDimensionException(fv.values.length, popts.dimension))
    case (SIMILARITY_HAMMING | SIMILARITY_JACCARD, ElastiKnnVector(SparseBoolVector(sbv))) =>
      if (sbv.totalIndices == popts.dimension) Success(()) else Failure(VectorDimensionException(sbv.totalIndices, popts.dimension))
    case _ => Failure(SimilarityAndTypeException(mopts.similarity, vec))
  }
}

case class JaccardLshRepresentation()
object JaccardLshRepresentation {
  implicit def encoder: Encoder[JaccardLshRepresentation] = deriveEncoder[JaccardLshRepresentation]
}

class JaccardLshModel(opts: JaccardLshOptions) extends VectorModel[JaccardLshRepresentation] {
  def apply(v: ElastiKnnVector)(implicit enc: Encoder[JaccardLshRepresentation],
                                dec: Decoder[JaccardLshRepresentation]): Try[JaccardLshRepresentation] = ???
}

object VectorModel {

  def toJson(popts: ProcessorOptions, vec: ElastiKnnVector): Try[Json] = (popts.modelOptions, vec) match {
    case (Exact(ex), _) => ExactModel(popts, ex, vec).map(_ => Json.fromJsonObject(JsonObject.empty))
    case _              => ???
  }

}
