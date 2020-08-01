package com.klibisz.elastiknn.models

import com.klibisz.elastiknn.api.{Mapping, Vec}
import org.scalatest.{FunSuite, Matchers}

import scala.util.Random

class L2LshSuite extends FunSuite with Matchers {

  implicit val rng = new Random(0)

  test("produces exactly L hashes with probes = 0") {
    val vec = Vec.DenseFloat.random(10)
    val lsh = new L2Lsh(Mapping.L2Lsh(vec.dims, 11, 2, 1))
    lsh(vec) should have length 11
    lsh.hashWithProbes(vec, 0) should have length 11
  }

  test("produces exactly L * (probes + 1) hashes") {
    def maxProbesForK(k: Int): Int = math.pow(3, k).toInt
    val vec = Vec.DenseFloat.random(100)
    for {
      l <- (1 to 10)
      k <- (1 to 5)
      lsh = new L2Lsh(Mapping.L2Lsh(vec.dims, l, k, 1))
      maxForK = maxProbesForK(k)
      p <- (0 to maxForK)
    } {
      withClue(s"L = $l, k = $k, p = $p") {
        lsh.hashWithProbes(vec, p) should have length (l * maxForK.min(p + 1))
      }
    }
  }

}
