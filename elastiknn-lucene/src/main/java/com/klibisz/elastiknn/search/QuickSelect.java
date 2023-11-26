package com.klibisz.elastiknn.search;

public class QuickSelect {

    public static int selectRecursive(int[] array, int n) {
        return recursive(array, 0, array.length - 1, n);
    }

    private static int recursive(int[] array, int left, int right, int k) {
        if (left == right) { // If the list contains only one element,
            return array[left]; // return that element
        }

        // select a pivotIndex between left and right
        int pivotIndex = middlePivot(left, right);
        pivotIndex = partition(array, left, right, pivotIndex);
        // The pivot is in its final sorted position
        if (k == pivotIndex) {
            return array[k];
        } else if (k < pivotIndex) {
            return recursive(array, left, pivotIndex - 1, k);
        } else {
            return recursive(array, pivotIndex + 1, right, k);
        }
    }

    private static int partition(int[] array, int left, int right, int pivotIndex) {
        int pivotValue = array[pivotIndex];
        swap(array, pivotIndex, right); // move pivot to end
        int storeIndex = left;
        for(int i = left; i < right; i++) {
            if(array[i] > pivotValue) {
                swap(array, storeIndex, i);
                storeIndex++;
            }
        }
        swap(array, right, storeIndex); // Move pivot to its final place
        return storeIndex;
    }

    private static void swap(int[] array, int a, int b) {
        int tmp = array[a];
        array[a] = array[b];
        array[b] = tmp;
    }

    private static int middlePivot(int left, int right) {
        return left + (right - left) / 2;
    }
}
