package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.storage.StoredVec

trait HashingFunction[M <: Mapping, V <: Vec, S <: StoredVec] extends (V => Array[Array[Byte]]) {
  val mapping: M
}
