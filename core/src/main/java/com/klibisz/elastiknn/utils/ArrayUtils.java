package com.klibisz.elastiknn.utils;

/**
 * Java implementations of some particularly performance-critical code paths.
 */
public class ArrayUtils {

    private static void unsortedException(int lit, int big) {
        throw new IllegalArgumentException(String.format("Called on unsorted array: %d came after %d", lit, big));
    }

    public interface IntIterator {
        int length();
        int get(int i);
    }

    public static int sortedIntersectionCount(int[] xs, int[] ys) {
        return sortedIntersectionCount(xs, new IntIterator() {
            @Override
            public int length() {
                return ys.length;
            }

            @Override
            public int get(int i) {
                return ys[i];
            }
        });
    }

    public static int sortedIntersectionCount(int [] xs, IntIterator ys) {
        int n = 0;
        int xi = 0;
        int yi = 0;
        int xmax = Integer.MIN_VALUE;
        int ymax = Integer.MIN_VALUE;
        while (xi < xs.length && yi < ys.length()) {
            int x = xs[xi];
            int y = ys.get(yi);
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
        return n;
    }

}
