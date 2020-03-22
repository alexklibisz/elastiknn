package com.klibisz.elastiknn.models

sealed trait SparseBoolVectorModelOptions

object ExactModelOptions extends SparseBoolVectorModelOptions
object JaccardIndexedModelOptions extends SparseBoolVectorModelOptions
case class JaccardLshModelOptions(bands: Int, rows: Int) extends SparseBoolVectorModelOptions
