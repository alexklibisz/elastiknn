package org.apache.lucene.search;

public class KthGreatest {

    public static class KthGreatestResult {
        public final short max;
        public final short kthGreatest;
        public final int numGreaterThanKthGreatest;
        public KthGreatestResult(short max, short kthGreatest, int numGreaterThanKthGreatest) {
            this.max = max;
            this.kthGreatest = kthGreatest;
            this.numGreaterThanKthGreatest = numGreaterThanKthGreatest;
        }
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
    public static KthGreatestResult kthGreatest(short[] arr, int k) {
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
            int numGreaterEqual = 0;
            short kthGreatest = max;
            while (kthGreatest >= min) {
                numGreaterEqual += hist[kthGreatest - min];;
                if (numGreaterEqual > k) break;
                else kthGreatest--;
            }
            int numGreater = numGreaterEqual - hist[kthGreatest - min];

            return new KthGreatestResult(max, kthGreatest, numGreater);
        }
    }
}
