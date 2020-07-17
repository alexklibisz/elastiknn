package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.StoredVec

// TODO: Return Array[(Array[Byte], Int)], where each entry is (hash, number of occurrences).
trait HashingFunction[M <: Mapping, V <: Vec, S <: StoredVec] extends (V => Array[Array[Byte]]) {
  val mapping: M
}
