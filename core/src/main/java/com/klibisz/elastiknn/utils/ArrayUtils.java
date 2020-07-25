package com.klibisz.elastiknn.utils;

import java.util.Arrays;

/**
 * Java implementations of some hot spots.
 */
public class ArrayUtils {

    /**
     * Compute the number of intersecting (i.e. identical) elements between two int arrays.
     * IMPORTANT: Assumes the given arrays are already sorted in ascending order and _does not_ check if this is true.
     * If the given arrays are not sorted, the answer will be wrong.
     * This is implemented in Java because for some reason Scala will Box the ints in some cases which is unnecessary
     * and far slower.
     * @param xs
     * @param ys
     * @return The number of identical elements in the two arrays. For example {1,2,3}, {2,3,4} would return 2.
     */
    public static int sortedIntersectionCount(final int [] xs, final int [] ys) {
        int n = 0;
        int xi = 0;
        int yi = 0;
        while (xi < xs.length && yi < ys.length) {
            int x = xs[xi];
            int y = ys[yi];
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

    /**
     * Find the kth greatest value in the given array of shorts in O(N) time and space.
     * Works by creating a histogram of the array values and traversing the histogram in reverse order.
     * Assumes the max value in the array is small enough that you can keep an array of that length in memory.
     * This is generally true for term counts.
     *
     * @param arr array of non-negative shorts, presumably some type of count.
     * @param k the desired largest value.
     * @return the kth largest value.
     */
    public static short kthGreatest(short[] arr, int k) {
        if (arr.length == 0) {
            throw new IllegalArgumentException("Array must be non-empty");
        } else if (k < 0 || k >= arr.length) {
            throw new IllegalArgumentException(String.format(
                    "k [%d] must be >= 0 and less than length of array [%d]",
                    k, arr.length
            ));
        } else {
            // Find the min and max values.
            short max = arr[0];
            short min = arr[0];
            for (short a: arr) {
                if (a > max) max = a;
                else if (a < min) min = a;
            }

            // Build and populate a histogram for non-zero values.
            int[] hist = new int[max - min + 1];
            for (short a: arr) {
                hist[a - min] += 1;
            }

            // Find the kth largest value by iterating from the end of the histogram.
            int geqk = 0;
            short kthLargest = max;
            while (kthLargest >= min) {
                geqk += hist[kthLargest - min];
                if (geqk > k) break;
                else kthLargest--;
            }

            return kthLargest;
        }
    }
}
