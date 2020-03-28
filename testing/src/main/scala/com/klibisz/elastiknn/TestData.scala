package com.klibisz.elastiknn

import com.klibisz.elastiknn.api.Vec

case class Query(vector: Vec, similarities: Vector[Float], indices: Vector[Int])
case class TestData(corpus: Vector[Vec], queries: Vector[Query])
