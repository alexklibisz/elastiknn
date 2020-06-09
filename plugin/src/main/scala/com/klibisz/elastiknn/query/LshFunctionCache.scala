package com.klibisz.elastiknn.query

import java.time.Duration

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.LshFunction
import com.klibisz.elastiknn.storage.StoredVec

// The Lsh Functions tend to be expensive to instantiate (i.e. initializing hashing parameters), hence a cache.
sealed trait LshFunctionCache[M <: Mapping, V <: Vec, S <: StoredVec] extends (M => LshFunction[M, V, S]) { self =>
  private val cache: LoadingCache[M, LshFunction[M, V, S]] = CacheBuilder.newBuilder
    .expireAfterWrite(Duration.ofSeconds(60))
    .build(new CacheLoader[M, LshFunction[M, V, S]] {
      override def load(m: M): LshFunction[M, V, S] = self.load(m)
    })
  override final def apply(mapping: M): LshFunction[M, V, S] = cache.get(mapping)
  protected def load(m: M): LshFunction[M, V, S]
}

object LshFunctionCache {
  implicit object Jaccard extends LshFunctionCache[Mapping.JaccardLsh, Vec.SparseBool, StoredVec.SparseBool] {
    def load(m: Mapping.JaccardLsh): LshFunction[Mapping.JaccardLsh, Vec.SparseBool, StoredVec.SparseBool] = new LshFunction.Jaccard(m)
  }
  implicit object Hamming extends LshFunctionCache[Mapping.HammingLsh, Vec.SparseBool, StoredVec.SparseBool] {
    def load(m: Mapping.HammingLsh): LshFunction[Mapping.HammingLsh, Vec.SparseBool, StoredVec.SparseBool] = new LshFunction.Hamming(m)
  }
  implicit object Angular extends LshFunctionCache[Mapping.AngularLsh, Vec.DenseFloat, StoredVec.DenseFloat] {
    def load(m: Mapping.AngularLsh): LshFunction[Mapping.AngularLsh, Vec.DenseFloat, StoredVec.DenseFloat] = new LshFunction.Angular(m)
  }
  implicit object L2 extends LshFunctionCache[Mapping.L2Lsh, Vec.DenseFloat, StoredVec.DenseFloat] {
    def load(m: Mapping.L2Lsh): LshFunction[Mapping.L2Lsh, Vec.DenseFloat, StoredVec.DenseFloat] = new LshFunction.L2(m)
  }
}
