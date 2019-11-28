package com.klibisz.elastiknn.elastic4s

object Dummy  {

  def main(args: Array[String]): Unit = {

    val a: Array[Double] = Array(
      0.34832991985182593,
      0.8089628600489587,
      0.45963718782542473,
      0.6261243048191758,
      0.20364683085153745,
      0.09367540477537306,
      0.9755259672661387,
      0.3673438283352697,
      0.5762839679431597,
      0.2459320679546172
    )

    val b: Array[Double] = Array(
      0.8133160836708041,
      0.7848667182769469,
      0.3934191124365154,
      0.8644791938019533,
      0.38403076811573245,
      0.2573028872050038,
      0.8294019198272359,
      0.7363827038385634,
      0.5076009080549594,
      0.644326615041417
    )

    var dotprod: Double = 0.0
    var asqsum: Double = 0.0
    var bsqsum: Double = 0.0

    for (i <- a.indices) {
      dotprod += a(i) * b(i)
      asqsum += a(i) * a(i)
      bsqsum += b(i) * b(i)
    }
    val sim: Double = dotprod / (Math.sqrt(asqsum) * Math.sqrt(bsqsum))
    println(1.0 + sim)

  }

}
