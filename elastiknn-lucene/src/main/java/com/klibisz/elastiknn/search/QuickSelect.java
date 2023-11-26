package com.klibisz.elastiknn.search;

public class QuickSelect {

    public static short selectRecursive(short[] array, int n) {
        return recursive(array, 0, array.length - 1, n);
    }

    private static short recursive(short[] array, int left, int right, int k) {
        if (left == right) { // If the list contains only one element,
            return array[left]; // return that element
        }

        // select a pivotIndex between left and right
        int pivotIndex = left + (right - left) / 2;
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

    private static int partition(short[] array, int left, int right, int pivotIndex) {
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

    private static void swap(short[] array, int a, int b) {
        short tmp = array[a];
        array[a] = array[b];
        array[b] = tmp;
    }
}
