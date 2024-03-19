package com.klibisz.elastiknn.api;

import java.util.Arrays;

public class FloatArrayBuffer {

  private float[] array;

  private int index = 0;

  public FloatArrayBuffer(int capacity) {
    this.array = new float[capacity];
  }

  public void append(float f) {
    if (index == this.array.length) {
      this.array = Arrays.copyOf(this.array, this.array.length * 2);
    }
    this.array[index++] = f;
//    try {
//      this.array[index++] = f;
//    } catch (IndexOutOfBoundsException ex) {
//      this.array = Arrays.copyOf(this.array, this.array.length * 2);
//      this.array[index - 1] = f;
//    }
  }

  public float[] toArray() {
    return Arrays.copyOf(this.array, index);
  }
}
