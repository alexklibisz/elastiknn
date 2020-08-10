package com.klibisz.elastiknn.models;

import java.util.Arrays;
import java.util.Random;

import static com.klibisz.elastiknn.storage.UnsafeSerialization.writeInt;
import static com.klibisz.elastiknn.storage.UnsafeSerialization.writeInts;

public class JaccardLshModel implements HashingModel.SparseBool {

    private final int L;
    private final int k;
    private final int[] A;
    private final int[] B;
    private final HashAndFreq[] empty;

    /**
     * Locality sensitive hashing model for Jaccard similarity.
     * Uses the well-known minhash method described in, among others, Mining Massive Datasets chapter 3.
     * Other resources used to implement this model:
     * - The Spark MinHashLsh implementation: https://spark.apache.org/docs/2.2.3/ml-features.html#minhash-for-jaccard-distance
     * - The tdebatty/java-LSH project on Github: https://github.com/tdebatty/java-LSH
     * - The "Minhash for dummies" blog post: http://matthewcasperson.blogspot.com/2013/11/minhash-for-dummies.html
     * @param L number of hash tables
     * @param k number of hash functions concatenated to form a hash for each table
     * @param rng random number generator used to instantiate model parameters
     */
    public JaccardLshModel(int L, int k, Random rng) {
        this.L = L;
        this.k = k;

        this.A = new int[L * k];
        for (int i = 0; i < L * k; i++) this.A[i] = rng.nextInt(HashingModel.HASH_PRIME - 1);

        this.B = new int[L * k];
        for (int i = 0; i < L * k; i++) this.B[i] = rng.nextInt(HashingModel.HASH_PRIME - 1);

        this.empty = new HashAndFreq[L];
        Arrays.fill(this.empty, HashAndFreq.once(writeInt(HashingModel.HASH_PRIME)));
    }

    @Override
    public HashAndFreq[] hash(int[] trueIndices, int totalIndices) {
        if (trueIndices.length == 0) {
            return this.empty;
        } else {
            HashAndFreq[] hashes = new HashAndFreq[L];
            for (int ixL = 0; ixL < L; ixL++) {
                int[] ints = new int[k + 1];
                ints[0] = ixL;
                for (int ixk = 0; ixk < k; ixk++) {
                    int a = A[ixL * k + ixk];
                    int b = B[ixL * k + ixk];
                    int minHash = Integer.MAX_VALUE;
                    for (int ti : trueIndices) {
                        int hash = ((1 + ti) * a + b) % HashingModel.HASH_PRIME;
                        if (hash < minHash) minHash = hash;
                    }
                    ints[ixk + 1] = minHash;
                }
                hashes[ixL] = HashAndFreq.once(writeInts(ints));
            }
            return hashes;
        }
    }
}
