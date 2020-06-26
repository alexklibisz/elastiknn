package com.klibisz.elastiknn.utils;

import java.util.Arrays;
import java.util.Random;

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
     * Find the kth largest value in the given array.
     * Swaps elements in the given array.
     * Copied from: https://github.com/bephrem1/backtobackswe, https://www.youtube.com/watch?v=hGK_5n81drs.
     * @param arr The array.
     * @param k The position.
     * @return The index of the kth largest value.
     */
    public static int quickSelect(int[] arr, int k) {
        Random rand = new Random(0);
        int n = arr.length;
        int left = 0;
        int right = n - 1;
        int finalIndexOfChoosenPivot = 0;
        while (left <= right) {
            int choosenPivotIndex = rand.nextInt(right - left + 1) + left;
            finalIndexOfChoosenPivot = qsPartition(arr, left, right, choosenPivotIndex);
            if (finalIndexOfChoosenPivot == n - k) {
                break;
            } else if (finalIndexOfChoosenPivot > n - k) {
                right = finalIndexOfChoosenPivot - 1;
            } else {
                left = finalIndexOfChoosenPivot + 1;
            }
        }
        return arr[finalIndexOfChoosenPivot];
    }

    /**
     * Same as quickSelect, except makes a copy of the array so the original is unmodified.
     * @param arr
     * @param k
     * @return
     */
    public static int quickSelectCopy(int[] arr, int k) {
        return quickSelect(Arrays.copyOf(arr, arr.length), k);
    }


    private static int qsPartition(int[] arr, int left, int right, int pivotIndex) {
        int pivotValue = arr[pivotIndex];
        int lesserItemsTailIndex = left;
        qsSwap(arr, pivotIndex, right);
        for (int i = left; i < right; i++) {
            if (arr[i] < pivotValue) {
                qsSwap(arr, i, lesserItemsTailIndex);
                lesserItemsTailIndex++;
            }
        }
        qsSwap(arr, right, lesserItemsTailIndex);
        return lesserItemsTailIndex;
    }

    private static void qsSwap(int[] arr, int first, int second) {
        int temp = arr[first];
        arr[first] = arr[second];
        arr[second] = temp;
    }


}
