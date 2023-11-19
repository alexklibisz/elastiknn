package com.klibisz.elastiknn.api

sealed trait Similarity

object Similarity {
  case object Cosine extends Similarity

  case object Hamming extends Similarity

  case object Jaccard extends Similarity

  case object L1 extends Similarity

  case object L2 extends Similarity

  val values: Seq[Similarity] = Vector(Cosine, Jaccard, Hamming, L1, L2)
}
