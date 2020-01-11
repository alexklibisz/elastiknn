package org.elasticsearch.elastiknn;

public class Dummy {

    public static void main(String[] args) {

        double[] a = {
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
        };

        double[] b = {
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
        };

        double dotprod = 0.0;
        double asqsum = 0.0;
        double bsqsum = 0.0;

        for (int i = 0; i < 1; i++) {
          dotprod += a[i] * b[i];
          asqsum += a[i] * a[i];
          bsqsum += b[i] * b[i];
        }

        System.out.println(dotprod);
        System.out.println(asqsum);
        System.out.println(bsqsum);

        double sim = dotprod / (Math.sqrt(asqsum) * Math.sqrt(bsqsum));
        System.out.println(1.0 + sim);

    }

}
