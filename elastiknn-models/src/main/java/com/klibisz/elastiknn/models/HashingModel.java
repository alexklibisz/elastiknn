package com.klibisz.elastiknn.models;

import java.util.Arrays;

public class HashingModel {

    public static int HASH_PRIME = 2038074743;

    public interface SparseBool {
        byte[][] hash(int[] trueIndices, int totalIndices);
    }

    public interface DenseFloat {
        byte[][] hash(float[] values);
    }

    public static int compareHashes(byte[] h1, byte[] h2) {
        return Arrays.compareUnsigned(h1, 0, h1.length, h2, 0, h2.length);
    }
}
