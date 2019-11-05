package com.klibisz.elastiknn.utils

import java.util

/** Zero-dependency LRU cache based on: http://chriswu.me/blog/a-lru-cache-in-10-lines-of-java */
private[elastiknn] class LRUCache[K, V](capacity: Int = 1000) {

  private val m = new util.LinkedHashMap[K, V](capacity, 0.75f, true) {
    override def removeEldestEntry(eldest: util.Map.Entry[K, V]): Boolean = size() > capacity
  }

  def put(k: K, v: V): Unit = this.synchronized(m.put(k, v))

  def get(k: K, fn: K => V): V = this.synchronized {
    if (m.containsKey(k)) m.get(k) else m.put(k, fn(k))
  }

}
