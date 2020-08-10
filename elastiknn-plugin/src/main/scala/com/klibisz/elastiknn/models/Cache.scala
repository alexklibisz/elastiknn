package com.klibisz.elastiknn.models

import java.util.Random

import com.google.common.cache._
import com.klibisz.elastiknn.api.Mapping

object Cache {

  private def cache[K <: Object, V <: Object](f: K => V): LoadingCache[K, V] =
    CacheBuilder.newBuilder
      .maximumSize(10)
      .build(new CacheLoader[K, V] {
        override def load(key: K): V = f(key)
      })

  private val angular = cache((m: Mapping.AngularLsh) => new AngularLshModel(m.dims, m.L, m.k, new Random(0)))
  private val jaccard = cache((m: Mapping.JaccardLsh) => new JaccardLshModel(m.L, m.k, new Random(0)))
  private val hamming = cache((m: Mapping.HammingLsh) => new HammingLshModel(m.dims, m.L, m.k, new Random(0)))
  private val permuttation = cache((m: Mapping.PermutationLsh) => new PermutationLshModel(m.dims, m.k, m.repeating))

  def apply(m: Mapping.AngularLsh): AngularLshModel = angular.get(m)
  def apply(m: Mapping.JaccardLsh): JaccardLshModel = jaccard.get(m)
  def apply(m: Mapping.HammingLsh): HammingLshModel = hamming.get(m)
  def apply(m: Mapping.PermutationLsh): PermutationLshModel = permuttation.get(m)

}
