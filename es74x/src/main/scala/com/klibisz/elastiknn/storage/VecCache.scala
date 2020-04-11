package com.klibisz.elastiknn.storage

import java.util.concurrent.TimeUnit

import com.google.common.cache.{Cache, CacheBuilder, CacheLoader, LoadingCache}
import com.klibisz.elastiknn.api.Vec
import org.apache.lucene.index.LeafReaderContext

private[elastiknn] object VecCache {

  type DocIdCache[V <: Vec] = Cache[Integer, V]
  type ContextCache[V <: Vec] = LoadingCache[LeafReaderContext, DocIdCache[V]]
  type IndexFieldCache[V <: Vec] = LoadingCache[(String, String), ContextCache[V]]

  private def build[V <: Vec]: IndexFieldCache[V] =
    CacheBuilder.newBuilder
      .expireAfterWrite(1, TimeUnit.MINUTES)
      .build(new CacheLoader[(String, String), ContextCache[V]] {
        override def load(key: (String, String)): LoadingCache[LeafReaderContext, DocIdCache[V]] =
          CacheBuilder.newBuilder.build(new CacheLoader[LeafReaderContext, DocIdCache[V]] {
            override def load(key: LeafReaderContext): DocIdCache[V] =
              CacheBuilder.newBuilder.build[Integer, V]
          })
      })

  private val sbv: IndexFieldCache[Vec.SparseBool] = build[Vec.SparseBool]
  private val dfv: IndexFieldCache[Vec.DenseFloat] = build[Vec.DenseFloat]

  def SparseBool(index: String, field: String): ContextCache[Vec.SparseBool] = sbv.get((index, field))
  def DenseFloat(index: String, field: String): ContextCache[Vec.DenseFloat] = dfv.get((index, field))

}
