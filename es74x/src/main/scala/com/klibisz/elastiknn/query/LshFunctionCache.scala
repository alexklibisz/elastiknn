package com.klibisz.elastiknn.query

import java.time.Duration

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models
import com.klibisz.elastiknn.models.LshFunction

// The Lsh Functions tend to be expensive to instantiate (i.e. initializing hashing parameters), hence a cache.
sealed trait LshFunctionCache[M <: Mapping, V <: Vec] extends (M => LshFunction[M, V]) { self =>
  private val cache: LoadingCache[M, LshFunction[M, V]] = CacheBuilder.newBuilder
    .expireAfterWrite(Duration.ofSeconds(60))
    .build(new CacheLoader[M, LshFunction[M, V]] {
      override def load(m: M): LshFunction[M, V] = self.load(m)
    })
  override final def apply(mapping: M): LshFunction[M, V] = cache.get(mapping)
  protected def load(m: M): LshFunction[M, V]
}

object LshFunctionCache {
  implicit object Jaccard extends LshFunctionCache[Mapping.JaccardLsh, Vec.SparseBool] {
    override protected def load(m: Mapping.JaccardLsh): LshFunction[Mapping.JaccardLsh, Vec.SparseBool] = new LshFunction.Jaccard(m)
  }
  implicit object Hamming extends LshFunctionCache[Mapping.HammingLsh, Vec.SparseBool] {
    override protected def load(m: Mapping.HammingLsh): LshFunction[Mapping.HammingLsh, Vec.SparseBool] = new models.LshFunction.Hamming(m)
  }
}
