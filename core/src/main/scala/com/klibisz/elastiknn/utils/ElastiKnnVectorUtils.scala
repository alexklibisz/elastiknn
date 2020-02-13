package com.klibisz.elastiknn.utils

import com.klibisz.elastiknn.{ElastiKnnVector, FloatVector, SparseBoolVector}
import scalapb.GeneratedMessageCompanion
import scalapb_circe.JsonFormat
import io.circe.syntax._

import scala.util.Try

trait ElastiKnnVectorUtils extends CirceUtils {

  implicit class ElastiKnnVectorCompanionImplicits(ekvc: GeneratedMessageCompanion[ElastiKnnVector]) {
    def apply(fv: FloatVector): ElastiKnnVector = ElastiKnnVector(ElastiKnnVector.Vector.FloatVector(fv))
    def apply(sbv: SparseBoolVector): ElastiKnnVector = ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv))
    def from(m: java.util.Map[String, AnyRef]): Try[ElastiKnnVector] = Try(JsonFormat.fromJson[ElastiKnnVector](m.asJson(javaMapEncoder)))
    def equal(ekv1: ElastiKnnVector, ekv2: ElastiKnnVector): Boolean = (ekv1, ekv2) match {
      case (ElastiKnnVector(ElastiKnnVector.Vector.FloatVector(fv1)), ElastiKnnVector(ElastiKnnVector.Vector.FloatVector(fv2))) =>
        fv1.values.sameElements(fv2.values)
      case (ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv1)),
            ElastiKnnVector(ElastiKnnVector.Vector.SparseBoolVector(sbv2))) =>
        sbv1.trueIndices.sameElements(sbv2.trueIndices) && sbv1.totalIndices == sbv2.totalIndices
      case _ => false
    }
  }

}

object ElastiKnnVectorUtils extends ElastiKnnVectorUtils
