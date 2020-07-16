package com.klibisz.elastiknn.query

import java.time.Duration

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.{AngularLsh, HammingLsh, HashingFunction, JaccardLsh, L2Lsh}
import com.klibisz.elastiknn.storage.StoredVec

// The Lsh Functions tend to be expensive to instantiate (i.e. initializing hashing parameters), hence a cache.
sealed trait HashingFunctionCache[M <: Mapping, V <: Vec, S <: StoredVec] extends (M => HashingFunction[M, V, S]) { self =>
  private val cache: LoadingCache[M, HashingFunction[M, V, S]] = CacheBuilder.newBuilder
    .expireAfterWrite(Duration.ofSeconds(60))
    .build(new CacheLoader[M, HashingFunction[M, V, S]] {
      override def load(m: M): HashingFunction[M, V, S] = self.load(m)
    })
  override final def apply(mapping: M): HashingFunction[M, V, S] = cache.get(mapping)
  protected def load(m: M): HashingFunction[M, V, S]
}

object HashingFunctionCache {
  implicit object Jaccard extends HashingFunctionCache[Mapping.JaccardLsh, Vec.SparseBool, StoredVec.SparseBool] {
    def load(m: Mapping.JaccardLsh): HashingFunction[Mapping.JaccardLsh, Vec.SparseBool, StoredVec.SparseBool] =
      new JaccardLsh(m)
  }
  implicit object Hamming extends HashingFunctionCache[Mapping.HammingLsh, Vec.SparseBool, StoredVec.SparseBool] {
    def load(m: Mapping.HammingLsh): HashingFunction[Mapping.HammingLsh, Vec.SparseBool, StoredVec.SparseBool] =
      new HammingLsh(m)
  }
  implicit object Angular extends HashingFunctionCache[Mapping.AngularLsh, Vec.DenseFloat, StoredVec.DenseFloat] {
    def load(m: Mapping.AngularLsh): HashingFunction[Mapping.AngularLsh, Vec.DenseFloat, StoredVec.DenseFloat] =
      new AngularLsh(m)
  }
  implicit object L2 extends HashingFunctionCache[Mapping.L2Lsh, Vec.DenseFloat, StoredVec.DenseFloat] {
    def load(m: Mapping.L2Lsh): HashingFunction[Mapping.L2Lsh, Vec.DenseFloat, StoredVec.DenseFloat] = new L2Lsh(m)
  }
}
