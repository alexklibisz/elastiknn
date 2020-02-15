package com.klibisz.elastiknn.reference;

import java.util.Arrays;
import java.util.Random;

public class BenchmarkSortedIntersectionJava {

    private static void unsortedException(int lit, int big) {
        throw new IllegalArgumentException(String.format("Called on unsorted array: %d came after %d", lit, big));
    }

    private static int sortedIntersectionCount(int [] xs, int [] ys) {
        int n = 0;
        int xi = 0;
        int yi = 0;
        int xmax = Integer.MIN_VALUE;
        int ymax = Integer.MIN_VALUE;
        while (xi < xs.length && yi < ys.length) {
            int x = xs[xi];
            int y = ys[yi];
            if (x < xmax) unsortedException(x, xmax);
            else xmax = x;
            if (y < ymax) unsortedException(y, ymax);
            else ymax = y;
            if (x < y) xi += 1;
            else if (x > y) yi += 1;
            else {
                n += 1;
                xi += 1;
                yi += 1;
            }
        }
        while(xi < xs.length) {
            if (xs[xi] < xmax) unsortedException(xs[xi], xmax);
            xi += 1;
        }
        while(yi < ys.length) {
            if (ys[yi] < ymax) unsortedException(ys[yi], ymax);
            yi += 1;
        }
        return n;
    }

    public static void main(String[] args) throws InterruptedException {

        Random rng = new Random(0L);
        int n = 5000000;

        int[] xs = new int[1000];
        int[] ys = new int[1000];

        for (int i = 0; i < xs.length; i++) {
            xs[i] = rng.nextInt(10000);
            ys[i] = rng.nextInt(10000);
        }

        Arrays.sort(xs);
        Arrays.sort(ys);
        System.out.println(sortedIntersectionCount(xs, ys));
        System.out.println(sortedIntersectionCount(ys, xs));

        Thread.sleep(5000);

        long t0 = System.currentTimeMillis();

        for (int i = 0; i < n; i++) {
            if (i % 2 == 0) sortedIntersectionCount(xs, ys);
            else sortedIntersectionCount(ys, xs);
        }

        System.out.println((System.currentTimeMillis() - t0) / 1000.0);
    }

}
