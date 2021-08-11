package com.klibisz.elastiknn.models;

import java.util.PriorityQueue;

public class PermutationLshModel implements HashingModel.DenseFloat {

    private final int k;
    private final boolean repeating;

    /**
     * Based on paper Large Scale Image Retrieval with Elasticsearch: https://dl.acm.org/doi/pdf/10.1145/3209978.3210089
     * Represents vectors by the indices of the largest absolute values, repeated proportionally to their rank in the vector.
     *
     * @param k number of highest absolute value indices to include as hashes of the vector
     * @param repeating whether or not to repeat the indices, which was enabled by default in the paper, but has shown mixed results
     */
    PermutationLshModel(int k, boolean repeating) {
        this.k = k;
        this.repeating = repeating;
    }

    @Override
    public HashAndFreq[] hash(float[] values) {

        PriorityQueue<Integer> indexHeap = new PriorityQueue<>((i1, i2) -> Float.compare(Math.abs(values[i2]), Math.abs(values[i1])));

        for (int i = 0; i < values.length; i++) indexHeap.add(i);

        // Build the array of hashes. The number of repetitions of each hash is represented by the HashAndFreq class.
        // Indexes of negative values are negated. Positive indexes are incremented by 1 and negative decremented by 1
        // to avoid ambiguity at zero. Ties are handled by repeating the tied indexes the same number of times, and
        // reducing subsequent repetition for each tie. Meaning if there's a two-way tie for 2nd place, there's no 3rd.
        HashAndFreq[] hashes = new HashAndFreq[k];
        int rankComplement = -1;
        int currTies = 0;
        float prevAbs = Float.POSITIVE_INFINITY;
        for (int ixHashes = 0; ixHashes < hashes.length && !indexHeap.isEmpty(); ixHashes++) {
            int ix = indexHeap.remove();
            float currAbs = Math.abs(values[ix]);
            if (currAbs < prevAbs) {
                rankComplement += 1 + currTies;
                prevAbs = currAbs;
                currTies = 0;
            } else currTies += 1;
            int hash = values[ix] >= 0 ? ix + 1 : -1 - ix;
            int freq = repeating ? k - rankComplement : 1;
            hashes[ixHashes] = new HashAndFreq(hash, freq);
        }
        return hashes;
    }
}
