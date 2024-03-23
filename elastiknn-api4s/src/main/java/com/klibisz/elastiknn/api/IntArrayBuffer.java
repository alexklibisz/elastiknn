package com.klibisz.elastiknn.api;

import java.util.Arrays;

public class IntArrayBuffer {

    // Track the last final capacity to exploit the fact that the current
    // vector length is probably the same as the last vector length.
    // Using a non-atomic because race conditions are unlikely to hurt.
    private static final int minInitialCapacity = 4;
    private static final int maxInitialCapacity = 4096;
    private static int nextInitialCapacity = minInitialCapacity;

    private int[] array;

    private int index = 0;

    public IntArrayBuffer() {
        this.array = new int[nextInitialCapacity];
    }

    public void append(int i) {
        // TODO: Test whether try/catch is faster than if.
//        try {
//            this.array[index++] = i;
//        } catch (IndexOutOfBoundsException ex) {
//            this.array = Arrays.copyOf(this.array, this.array.length * 2);
//            this.array[index - 1] = i;
//        }
        if (index == this.array.length) {
            this.array = Arrays.copyOf(this.array, this.array.length * 2);
        }
        this.array[index++] = i;
    }

    public int[] toArray() {
        nextInitialCapacity = Math.min(maxInitialCapacity, Math.max(minInitialCapacity, index));
        if (this.array.length == index) {
            return this.array;
        } else {
            return Arrays.copyOf(this.array, index);
        }
    }
}