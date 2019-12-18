package com.klibisz.elastiknn.models

import com.google.common.cache.{CacheBuilder, CacheLoader}
import com.klibisz.elastiknn.ElastiKnnVector.Vector.{FloatVector, SparseBoolVector}
import com.klibisz.elastiknn.ProcessorOptions.ModelOptions._
import com.klibisz.elastiknn.Similarity._
import com.klibisz.elastiknn._
import io.circe._
import io.circe.syntax._

import scala.util._

object ExactModel {
  def apply(popts: ProcessorOptions, mopts: ExactModelOptions, vec: ElastiKnnVector): Try[Unit] = (mopts.similarity, vec) match {
    case (SIMILARITY_ANGULAR | SIMILARITY_L1 | SIMILARITY_L2, ElastiKnnVector(FloatVector(fv))) =>
      if (fv.values.length == popts.dimension) Success(()) else Failure(VectorDimensionException(fv.values.length, popts.dimension))
    case (SIMILARITY_HAMMING | SIMILARITY_JACCARD, ElastiKnnVector(SparseBoolVector(sbv))) =>
      if (sbv.totalIndices == popts.dimension) Success(()) else Failure(VectorDimensionException(sbv.totalIndices, popts.dimension))
    case _ => Failure(SimilarityAndTypeException(mopts.similarity, vec))
  }
}

class JaccardLshModel(opts: JaccardLshOptions) {

  private val rng: Random = new Random(opts.seed)
//  private val hashFuncs: Seq[]

  def apply(vec: ElastiKnnVector): Try[Map[String, String]] = {
    ???
  }
}

object VectorModel {

  private val jaccardCache = CacheBuilder.newBuilder.build(new CacheLoader[JaccardLshOptions, JaccardLshModel] {
    def load(opts: JaccardLshOptions): JaccardLshModel = new JaccardLshModel(opts)
  })

  def toJson(popts: ProcessorOptions, vec: ElastiKnnVector): Try[Json] = (popts.modelOptions, vec) match {
    case (Exact(mopts), _)   => ExactModel(popts, mopts, vec).map(_ => Json.fromJsonObject(JsonObject.empty))
    case (Jaccard(mopts), _) => jaccardCache.get(mopts)(vec).map(_.asJson)
    case _                   => ???
  }

}
