package com.klibisz.elastiknn.models;

public class KthGreatest {
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
