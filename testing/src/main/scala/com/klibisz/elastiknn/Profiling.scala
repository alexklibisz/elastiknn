package com.klibisz.elastiknn

import com.klibisz.elastiknn
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.models.{JaccardLshModel, LshFunction}
import com.klibisz.elastiknn.utils.ArrayUtils

import scala.util.Random

// Simple apps that make it easy to profile hotspots using VisualVM.
// One quirk with using VisualVM for profiling is that it has to be running on the same JVM as the app.
// For me it was enough to folow this comment: https://github.com/oracle/visualvm/issues/130#issuecomment-483898542

object ProfileVectorHashing {
  def main(args: Array[String]): Unit = {
    implicit val r: Random = new Random(100)
    val m = new elastiknn.models.LshFunction.Jaccard(Mapping.JaccardLsh(100, 150, 1))
    val vecs = Vec.SparseBool.randoms(100, 5000)
    while (true) {
      val t0 = System.currentTimeMillis()
      vecs.foreach(v => m(v))
      println(vecs.length * 1.0 / (System.currentTimeMillis() - t0) * 1000)
    }
  }
}

object ProfileSortedIntersection {
  def main(args: Array[String]): Unit = {
    implicit val r: Random = new Random(100)
    val vecs = Vec.SparseBool.randoms(100, 5000)
    while (true) {
      val t0 = System.currentTimeMillis()
      vecs.drop(1).zip(vecs).map {
        case (a, b) => ArrayUtils.sortedIntersectionCount(a.trueIndices, b.trueIndices)
      }
      println(vecs.length * 1.0 / (System.currentTimeMillis() - t0) * 1000)
    }
  }
}
