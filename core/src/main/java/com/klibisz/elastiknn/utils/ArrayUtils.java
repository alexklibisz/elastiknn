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
     * Return the k-th largest value in the array.
     * Makes a copy of the array so the original is not modified.
     * @param arr an array of ints, usually counts of some sort.
     * @param k the position of interest, usually a cutoff of some sort.
     * @return the k-th largest value in the array.
     */
    public static int quickSelect(final int[] arr, final int k) {
        return quickSelect(Arrays.copyOf(arr, arr.length), 0, arr.length - 1, k);
    }

    private static int quickSelect(int[] arr, int lo, int hi, int k) {
        if (lo == hi) {
            return arr[lo];
        } else {
            int p = arr[(lo + hi) / 2];
            int i = lo - 1;
            int j = hi + 1;
            while (true) {
                while (arr[++i] > p);
                while (arr[--j] < p);
                if (i >= j) break;
                int tmp = arr[i];
                arr[i] = arr[j];
                arr[j] = tmp;
            }
            if (k <= j) return quickSelect(arr, lo, j, k);
            else return quickSelect(arr, j + 1, hi, k);
        }
    }

//    /**
//     * Find the kth largest value in the given array.
//     * Swaps elements in the given array.
//     * Based on: https://github.com/bephrem1/backtobackswe, https://www.youtube.com/watch?v=hGK_5n81drs.
//     * Lucene also has an implementation: https://lucene.apache.org/core/8_0_0/core/org/apache/lucene/util/IntroSelector.html,
//     * but it's more abstract and was slower when I benchmarked it.
//     * @param arr The array.
//     * @param k The position.
//     * @return The index of the kth largest value.
//     */
//    public static int quickSelect(int[] arr, int k) {
//        int n = arr.length;
//        int left = 0;
//        int right = n - 1;
//        int finalIndexOfChoosenPivot = 0;
//        while (left <= right) {
//            int choosenPivotIndex = (right - left + 1) / 2 + left;
//            finalIndexOfChoosenPivot = qsPartition(arr, left, right, choosenPivotIndex);
//            if (finalIndexOfChoosenPivot == n - k) {
//                break;
//            } else if (finalIndexOfChoosenPivot > n - k) {
//                right = finalIndexOfChoosenPivot - 1;
//            } else {
//                left = finalIndexOfChoosenPivot + 1;
//            }
//        }
//        return arr[finalIndexOfChoosenPivot];
//    }
//
//    /**
//     * Same as quickSelect, except makes a copy of the array so the original is unmodified.
//     * @param arr
//     * @param k
//     * @return
//     */
//    public static int quickSelectCopy(int[] arr, int k) {
//        return quickSelect(Arrays.copyOf(arr, arr.length), k);
//    }
//
//
//    private static int qsPartition(int[] arr, int left, int right, int pivotIndex) {
//        int pivotValue = arr[pivotIndex];
//        int lesserItemsTailIndex = left;
//        qsSwap(arr, pivotIndex, right);
//        for (int i = left; i < right; i++) {
//            if (arr[i] < pivotValue) {
//                qsSwap(arr, i, lesserItemsTailIndex);
//                lesserItemsTailIndex++;
//            }
//        }
//        qsSwap(arr, right, lesserItemsTailIndex);
//        return lesserItemsTailIndex;
//    }
//
//    private static void qsSwap(int[] arr, int first, int second) {
//        int temp = arr[first];
//        arr[first] = arr[second];
//        arr[second] = temp;
//    }


}
