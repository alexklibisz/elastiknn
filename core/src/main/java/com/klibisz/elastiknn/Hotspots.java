package com.klibisz.elastiknn;

public class Hotspots {

    private static void unsortedException(int lit, int big) {
        throw new IllegalArgumentException(String.format("Called on unsorted array: %d came after %d", lit, big));
    }

    public static int sortedIntersectionCount(int [] xs, int [] ys) {
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


}
