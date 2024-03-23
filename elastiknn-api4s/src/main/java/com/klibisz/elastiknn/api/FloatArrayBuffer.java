package com.klibisz.elastiknn.api;

import java.util.Arrays;

public class FloatArrayBuffer {

    // Track the last final capacity to exploit the fact that the current
    // vector length is probably the same as the last vector length.
    // Using a non-atomic because race conditions are unlikely to hurt.
    private static final int minInitialCapacity = 4;
    private static final int maxInitialCapacity = 4096;
    private static int nextInitialCapacity = minInitialCapacity;

    private float[] array;

    private int index = 0;

    public FloatArrayBuffer() {
//        System.out.printf("Starting at %d\n", nextInitialCapacity);
        this.array = new float[nextInitialCapacity];
    }

    public void append(float f) {
        // TODO: Test whether try/catch is faster than if.
//        try {
//          this.array[index++] = f;
//        } catch (IndexOutOfBoundsException ex) {
//          this.array = Arrays.copyOf(this.array, this.array.length * 2);
//          this.array[index - 1] = f;
//        }
        if (index == this.array.length) {
//            System.out.printf("Growing from %d to %d\n", this.array.length, this.array.length * 2);
            this.array = Arrays.copyOf(this.array, this.array.length * 2);
        }
        this.array[index++] = f;
    }

    public float[] toArray() {
        nextInitialCapacity = Math.min(maxInitialCapacity, Math.max(minInitialCapacity, index));
        if (this.array.length == index) {
            return this.array;
        } else {
            return Arrays.copyOf(this.array, index);
        }
    }
}
