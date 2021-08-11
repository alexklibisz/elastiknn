package com.klibisz.elastiknn.models;

public class HashingModel {

    public static int HASH_PRIME = 2038074743; // Copied from Spark.
    public static int MURMURHASH_SEED = 0x9747b28c; // Copied from Lucene.

    public interface SparseBool {
        HashAndFreq[] hash(int[] trueIndices, int totalIndices);
    }

    public interface DenseFloat {
        HashAndFreq[] hash(float[] values);
    }

}
