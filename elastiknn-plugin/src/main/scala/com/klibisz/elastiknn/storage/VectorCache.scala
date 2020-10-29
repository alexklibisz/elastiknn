package com.klibisz.elastiknn.storage

import java.util.concurrent.TimeUnit

import com.google.common.cache.{Cache, CacheBuilder}

trait VectorCache[S <: StoredVec] {
  def apply(key: VectorCache.Key, set: () => S): S
}

object VectorCache {

  type DenseCache = VectorCache[StoredVec.DenseFloat]
  type SparseCache = VectorCache[StoredVec.SparseBool]

  case class Key(docId: Int, field: String, contextHashCode: Long)

  def apply[S <: StoredVec](concurrency: Int, capacityBytes: Long, ttlSeconds: Long): VectorCache[S] = new VectorCache[S] {
    private val cache: Cache[Key, S] = CacheBuilder.newBuilder
      .concurrencyLevel(concurrency)
      .maximumWeight(capacityBytes)
      .weigher((_: Key, value: S) => value.sizeBytes)
      .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
      .build()
    override def apply(key: Key, set: () => S): S = cache.get(key, () => set())
  }

  def empty[S <: StoredVec]: VectorCache[S] = (_, set: () => S) => set()

}
