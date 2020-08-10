package com.klibisz.elastiknn.models;

import com.klibisz.elastiknn.storage.BitBuffer;
import com.klibisz.elastiknn.storage.UnsafeSerialization;

import java.util.*;
import java.util.stream.Collectors;

public class HammingLshModel implements HashingModel.SparseBool{

    private final int L;
    private final int k;

    // Represents an index in the vector that should be checked, and the hash indices which should be updated based on
    // the value at that vector index. e.g. (99, (1, 3, 12)) means to check if 99 is present in the vector. If it is,
    // you'll append a 1 to hashes 1, 3, and 12. Otherwise you'll append a 0 to those hashes.
    private final SampledPosition[] sampledPositions;

    /**
     * Locality sensitive hashing model for Hamming similarity.
     * Based on the index sampling technique described in, among others, Mining Massive Datasets chapter 3.
     * Includes one modification to the traditional method. Specifically, there are L hash tables, and each table's
     * hash function is a concatenation of k randomly sampled bits from the vector. The original method would just
     * a series of single bits and use each one as a distinct hash value.
     *
     * @param dims length of the vectors hashed by this model
     * @param L number of hash tables
     * @param k number of hash functions concatenated to form a hash for each table
     * @param rng random number generator used to instantiate model parameters
     */
    HammingLshModel(int dims, int L, int k, Random rng) {
        this.L = L;
        this.k = k;

        // Build (vector index, hash index) pairs.
        IndexPair[] indexPairs = new IndexPair[L * k];
        if (L * k <= dims) {
            int[] sample = sampleNoReplacement(rng, L * k, dims);
            for (int i = 0; i < L * k; i++) {
                indexPairs[i] = new IndexPair(sample[i], i % L);
            }
        } else {
            for (int ixL = 0; ixL < L; ixL++) {
                int[] sample = sampleNoReplacement(rng, k, dims);
                for (int ixk = 0; ixk < k; ixk++) {
                    indexPairs[ixL * k + ixk] = new IndexPair(sample[ixk], ixL);
                }
            }
        }

        // Re-arrange pairs for efficient iteration in the hash method.
        this.sampledPositions = Arrays.stream(indexPairs)
            .collect(Collectors.groupingBy((p) -> p.vecIndex, Collectors.toList()))
            .entrySet()
            .stream()
            .map(e -> new SampledPosition(
                    e.getKey(),
                    e.getValue().stream().map(p -> p.hashIndex).mapToInt(Integer::intValue).toArray())
            )
            .sorted(Comparator.comparing(o -> o.vecIndex))
            .toArray(SampledPosition[]::new);
    }

    private static class SampledPosition {
        final int vecIndex;
        final int[] hashIndexes;
        SampledPosition(int vecIndex, int[] hashIndexes) {
            this.vecIndex = vecIndex;
            this.hashIndexes = hashIndexes;
        }
    }

    private static class IndexPair {
        final int vecIndex;
        final int hashIndex;
        IndexPair(int vecIndex, int hashIndex) {
            this.vecIndex = vecIndex;
            this.hashIndex = hashIndex;
        }
    }

    private static int[] sampleNoReplacement(Random rng, int n, int max) {
        Set<Integer> seen = new HashSet<>(n);
        int[] sample = new int[n];
        while (seen.size() < Math.min(n, max)) {
            int next = rng.nextInt(max);
            if (!seen.contains(next)) {
                sample[seen.size()] = next;
                seen.add(next);
            }
        }
        return sample;
    }

    @Override
    public HashAndFreq[] hash(int[] trueIndices, int totalIndices) {
        BitBuffer.IntBuffer[] hashBuffers = new BitBuffer.IntBuffer[L];
        for (int ixL = 0; ixL < L; ixL++) {
            hashBuffers[ixL] = new BitBuffer.IntBuffer(UnsafeSerialization.writeInt(ixL));
        }
        int ixsp = 0;
        int ixti = 0;
        while (ixti < trueIndices.length && ixsp < sampledPositions.length) {
            int trueIndex = trueIndices[ixti];
            SampledPosition spos = sampledPositions[ixsp];
            if (spos.vecIndex > trueIndex) ixti += 1;
            else if (spos.vecIndex < trueIndex) {
                for (int hi : spos.hashIndexes) hashBuffers[hi].putZero();
                ixsp += 1;
            } else {
                for (int hi : spos.hashIndexes) hashBuffers[hi].putOne();
                ixsp += 1;
            }
        }
        while (ixsp < sampledPositions.length) {
            for (int hi: sampledPositions[ixsp].hashIndexes) hashBuffers[hi].putZero();
            ixsp += 1;
        }
        HashAndFreq[] hashes = new HashAndFreq[L];
        for (int ixL = 0; ixL < hashBuffers.length; ixL++) hashes[ixL] = HashAndFreq.once(hashBuffers[ixL].toByteArray());
        return hashes;
    }
}
