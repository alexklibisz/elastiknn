package com.klibisz.elastiknn.models;

public class HashingModel {

    public static int HASH_PRIME = 2038074743;

    public interface SparseBool {
        HashAndFreq[] hash(int[] trueIndices, int totalIndices);
    }

    public interface DenseFloat {
        HashAndFreq[] hash(float[] values);
    }

}
