package com.klibisz.elastiknn

import com.klibisz.elastiknn
import com.klibisz.elastiknn.api.{Mapping, Vec}
import com.klibisz.elastiknn.utils.ArrayUtils

import scala.util.Random

// Simple apps that make it easy to profile hotspots using VisualVM.
// One quirk with using VisualVM for profiling is that it has to be running on the same JVM as the app.
// For me it was enough to folow this comment: https://github.com/oracle/visualvm/issues/130#issuecomment-483898542

object ProfileVectorHashing {
  def main(args: Array[String]): Unit = {
    implicit val r: Random = new Random(100)
    val m = new elastiknn.models.JaccardLsh(Mapping.JaccardLsh(100, 150, 1))
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

object PairingFunctions {
  def main(args: Array[String]): Unit = {
    // Based on https://stackoverflow.com/a/14051714
    def szudzik(a: Int, b: Int): Int = {
      val c = if (a >= 0) 2 * a else -2 * a - 1
      val d = if (b >= 0) 2 * b else -2 * b - 1
      if (c >= d) c * c + c + d else c + d * d
    }
    val r = new Random(System.currentTimeMillis())
    val n = 10
    var uniq = Set.empty[Int]
    var i = 0
    while (i - uniq.size < 100) {
      var bandHash = r.nextInt(80)
      (0 until n)
        .map(_ => r.nextInt(Int.MaxValue) - r.nextInt(Int.MaxValue))
        .foreach(h => bandHash = szudzik(bandHash, h))
      uniq = uniq + bandHash
      i += 1
      println(s"$i, ${uniq.size}")
    }
  }
}
