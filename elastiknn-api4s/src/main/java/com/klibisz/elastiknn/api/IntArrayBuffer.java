package com.klibisz.elastiknn.api;

import java.util.Arrays;

public class IntArrayBuffer {

    private int[] array;

    private int index = 0;

    public IntArrayBuffer(int capacity) {
        this.array = new int[capacity];
    }

    public void append(int i) {
        try {
            this.array[index++] = i;
        } catch (IndexOutOfBoundsException ex) {
            this.array = Arrays.copyOf(this.array, this.array.length * 2);
            this.array[index - 1] = i;
        }
    }

    public int[] toArray() {
        return Arrays.copyOf(this.array, index);
    }
}
