package com.elastiknn.annb

import com.klibisz.elastiknn.api.Vec

sealed trait VecType[V1, V2 <: Vec.KnownDims] {
  def apply(v1: V1): V2
}
