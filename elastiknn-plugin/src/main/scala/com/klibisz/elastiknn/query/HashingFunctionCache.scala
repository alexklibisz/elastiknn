package com.klibisz.elastiknn.query

import com.google.common.cache._
import com.klibisz.elastiknn.api._
import com.klibisz.elastiknn.models._
import com.klibisz.elastiknn.storage.StoredVec

// The Lsh Functions tend to be expensive to instantiate (i.e. initializing hashing parameters), hence a cache.
sealed trait HashingFunctionCache[M <: Mapping, V <: Vec, S <: StoredVec, F <: HashingFunction[M, V, S]] extends (M => F) { self =>
  private val cache: LoadingCache[M, F] = CacheBuilder.newBuilder
    .maximumSize(10)
    .build(new CacheLoader[M, F] {
      override def load(m: M): F = self.load(m)
    })
  override final def apply(mapping: M): F = cache.get(mapping)
  protected def load(m: M): F
}

object HashingFunctionCache {
  implicit object Hamming extends HashingFunctionCache[Mapping.HammingLsh, Vec.SparseBool, StoredVec.SparseBool, HammingLsh] {
    def load(m: Mapping.HammingLsh): HammingLsh = new HammingLsh(m)
  }
  implicit object L2 extends HashingFunctionCache[Mapping.L2Lsh, Vec.DenseFloat, StoredVec.DenseFloat, L2Lsh] {
    def load(m: Mapping.L2Lsh): L2Lsh = new L2Lsh(m)
  }
  implicit object Permutation extends HashingFunctionCache[Mapping.PermutationLsh, Vec.DenseFloat, StoredVec.DenseFloat, PermutationLsh] {
    def load(m: Mapping.PermutationLsh): PermutationLsh = new PermutationLsh(m)
  }
}
