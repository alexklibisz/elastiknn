package com.klibisz.elastiknn.api

sealed trait NearestNeighborsQuery {
  def field: String

  def vec: Vec

  def similarity: Similarity

  def withVec(v: Vec): NearestNeighborsQuery
}

object NearestNeighborsQuery {
  sealed trait ApproximateQuery extends NearestNeighborsQuery {
    def candidates: Int

    def withCandidates(candidates: Int): ApproximateQuery
  }

  final case class Exact(field: String, similarity: Similarity, vec: Vec = Vec.Empty()) extends NearestNeighborsQuery {
    override def withVec(v: Vec): NearestNeighborsQuery = copy(vec = v)
  }

  final case class CosineLsh(field: String, candidates: Int, vec: Vec = Vec.Empty()) extends ApproximateQuery {
    override def withVec(v: Vec): NearestNeighborsQuery = copy(vec = v)

    override def withCandidates(candidates: Int): ApproximateQuery = copy(candidates = candidates)

    override def similarity: Similarity = Similarity.Cosine
  }

  final case class HammingLsh(field: String, candidates: Int, vec: Vec = Vec.Empty()) extends ApproximateQuery {
    override def withVec(v: Vec): NearestNeighborsQuery = copy(vec = v)

    override def withCandidates(candidates: Int): ApproximateQuery = copy(candidates = candidates)

    override def similarity: Similarity = Similarity.Hamming
  }

  final case class JaccardLsh(field: String, candidates: Int, vec: Vec = Vec.Empty()) extends ApproximateQuery {
    override def withVec(v: Vec): NearestNeighborsQuery = copy(vec = v)

    override def withCandidates(candidates: Int): ApproximateQuery = copy(candidates = candidates)

    override def similarity: Similarity = Similarity.Jaccard
  }

  final case class L2Lsh(field: String, candidates: Int, probes: Int = 0, vec: Vec = Vec.Empty()) extends ApproximateQuery {
    override def withVec(v: Vec): NearestNeighborsQuery = copy(vec = v)

    override def withCandidates(candidates: Int): ApproximateQuery = copy(candidates = candidates)

    override def similarity: Similarity = Similarity.L2
  }

  final case class PermutationLsh(field: String, similarity: Similarity, candidates: Int, vec: Vec = Vec.Empty())
    extends ApproximateQuery {
    override def withVec(v: Vec): NearestNeighborsQuery = copy(vec = v)

    override def withCandidates(candidates: Int): ApproximateQuery = copy(candidates = candidates)
  }
}