package com.klibisz.elastiknn.models;

import java.util.*;

import static com.klibisz.elastiknn.models.Utils.dot;
import static com.klibisz.elastiknn.storage.UnsafeSerialization.writeInts;

public class L2LshModel implements HashingModel.DenseFloat {

    private final int L;
    private final int k;
    private final int w;
    private final int maxProbesPerTable;
    private final float[][] A;
    private final float[] B;

    /**
     * Locality sensitive hashing with multi-probe hashing for L2 similarity.
     *
     * Based on Mining Massive Datasets chapter 3 and Multi-Probe LSH paper by Qin et. al. 2007.
     * Also drew some inspiration from this closed PR: https://github.com/elastic/elasticsearch/pull/44374.
     *
     * Parameter tuning:
     *  - L is the number hash tables. Increasing this parameter generally increases recall.
     *  - k is the number of hash functions concatenated to form a hash for each table. Increasing k generally
     *    increases precision.
     *  - w is the width of each hash bucket. Increasing this parameter generally increases recall. However, this
     *    parameter should also be tuned based on the magnitude of of the vector values. E.g., vectors containing very
     *    small float values should generally use smaller w.
     *
     * Hash sizes:
     *  - A model with L hash tables and k functions will produce L hashes, each containing 4 * 4 * k bytes.
     *    E.g., L = 9, k = 3 will produce 9 hashes, each containing 4 * 4 * 3 = 48 bytes.
     *  - For a dataset w/ N vectors, the max storage needed for just the hashes is N * L * (4 + 4 * k) bytes.
     *    E.g., N = 100k, L = 9, k = 3 requires at most 100k * 9 * (4 + 4 * 3) bytes = 14.4MB.
     *  - Increasing w will decrease the storage size, as more vectors will be assigned to the same hashes.
     *
     * The multi-probe LSH implementation follows Qin et. al. with some subtle exceptions:
     * - Doesn't use the score estimation described in section 4.5. Doesn't seem necessary as generating perturbation
     *   sets is not a performance bottleneck.
     * - Keeps a single heap of perturbation sets across all tables. They actually mention this as an option in the
     *   paper, but it's not clearly directed.
     * - The shift and expand methods are smart enough to always generate valid perturbation sets, so you'll never
     *   append an invalid one to the heap. This simplifies the logic for Algorithm 1.
     *
     * @param dims length of the vectors hashed by this model
     * @param L number of hash tables
     * @param k number of hash functions concatenated to form a hash for each table
     * @param w width of each hash bucket
     * @param rng random number generator used to instantiate model parameters.
     */
    public L2LshModel(int dims, int L, int k, int w, Random rng) {
        this.L = L;
        this.k = k;
        this.w = w;

        // 3 possible perturbations (-1, 0, 1) for each of k hashes. Subtract one for the all-zeros case.
        this.maxProbesPerTable = (int) Math.pow(3d, k) - 1;

        // Populate the random parameters.
        this.A = new float[L * k][dims];
        for (int ixL = 0; ixL < L; ixL++) {
            for (int ixk = 0; ixk < k; ixk++) {
                for (int ixdims = 0; ixdims < dims; ixdims++) {
                    this.A[ixL * k + ixk][ixdims] = (float) rng.nextGaussian();
                }
            }
        }
        this.B = new float[L * k];
        for (int ixL = 0; ixL < L; ixL++) {
            for (int ixk = 0; ixk < k; ixk++) {
                this.B[ixL * k + ixk] = rng.nextFloat() * w;
            }
        }
    }

    @Override
    public HashAndFreq[] hash(float[] values) {
        return hash(values, 0);
    }

    private HashAndFreq[] hashNoProbing(float[] values) {
        HashAndFreq[] hashes = new HashAndFreq[L];
        for (int ixL = 0; ixL < L; ixL++) {
            int[] ints = new int[1 + k];
            ints[0] = ixL;
            for (int ixk = 0; ixk < k; ixk++) {
                float[] a = A[ixL * k + ixk];
                float b = B[ixL * k + ixk];
                ints[ixk + 1] = (int) Math.floor((dot(a, values) + b) / w);
            }
            hashes[ixL] = HashAndFreq.once(writeInts(ints));
        }
        return hashes;
    }

    private HashAndFreq[] hashWithProbing(float[] values, int probesPerTable) {
        int numHashes = L * (1 + Math.max(0, Math.min(probesPerTable, maxProbesPerTable)));
        HashAndFreq[] hashes = new HashAndFreq[numHashes];
        // Populate the non-perturbed hashes, generate all non-perturbations, and generate all +1/-1 perturbations.
        Perturbation[] zeroPerturbations = new Perturbation[L * k];
        Perturbation[][] sortedPerturbations = new Perturbation[L][k * 2];
        for (int ixL = 0; ixL < L; ixL++) {
            int[] ints = new int[k + 1];
            ints[0] = ixL;
            for (int ixk = 0; ixk < k; ixk++) {
                float[] a = A[ixL * k + ixk];
                float b = B[ixL * k + ixk];
                float proj = dot(a, values) + b;
                int hash = (int) Math.floor(proj / w);
                float dneg = proj - hash * w;
                sortedPerturbations[ixL][ixk * 2 + 0] = new Perturbation(ixL, ixk, -1, proj, hash, Math.abs(dneg));
                sortedPerturbations[ixL][ixk * 2 + 1] = new Perturbation(ixL, ixk, 1, proj, hash, Math.abs(w - dneg));
                zeroPerturbations[ixL * k + ixk] = new Perturbation(ixL, ixk, 0, proj, hash, 0);
                ints[ixk + 1] = hash;
            }
            hashes[ixL] = HashAndFreq.once(writeInts(ints));
        }

        PriorityQueue<PerturbationSet> heap = new PriorityQueue<>((o1, o2) -> Float.compare(o1.absDistsSum, o2.absDistsSum));

        // Sort the perturbations in ascending order by abs. distance and add the head of each sorted array to the heap.
        for (int ixL = 0; ixL < L; ixL++) {
            Arrays.sort(sortedPerturbations[ixL], (o1, o2) -> Float.compare(o1.absDistance, o2.absDistance));
            heap.add(PerturbationSet.single(sortedPerturbations[ixL][0]));
        }

        // Start at L because the first L non-perturbed hashes were added above.
        for (int ixhashes = L; ixhashes < hashes.length; ixhashes++) {
            // Extract top perturbation set and add the shifted/expanded versions.
            // Different from the paper, assumes shift/expand can only return valid perturbation sets, or return null.
            PerturbationSet Ai = heap.remove();
            PerturbationSet As = PerturbationSet.shift(sortedPerturbations[Ai.ixL], Ai);
            PerturbationSet Ae = PerturbationSet.expand(sortedPerturbations[Ai.ixL], Ai);
            if (As != null) heap.add(As);
            if (Ae != null) heap.add(Ae);

            // Generate the hash value for Ai. If ixk is unperturbed access the zeroPerturbations from above.
            int[] ints = new int[k + 1];
            ints[0] = Ai.ixL;
            for (int ixk = 0; ixk < k; ixk++) {
                Perturbation pert = Ai.members.getOrDefault(ixk, zeroPerturbations[Ai.ixL * k + ixk]);
                ints[ixk + 1] = pert.hash + pert.delta;
            }
            hashes[ixhashes] = HashAndFreq.once(writeInts(ints));
        }
        return hashes;
    }


    public HashAndFreq[] hash(float[] values, int probesPerTable) {
        if (probesPerTable == 0) return hashNoProbing(values);
        else return hashWithProbing(values, probesPerTable);
    }

    private static class Perturbation {
        final int ixL;
        final int ixk;
        final int delta;
        final float projection;
        final int hash;
        final float absDistance;
        private Perturbation(int ixL, int ixk, int delta, float projection, int hash, float absDistance) {
            this.ixL = ixL;
            this.ixk = ixk;
            this.delta = delta;
            this.projection = projection;
            this.hash = hash;
            this.absDistance = absDistance;
        }
    }

    private static class PerturbationSet {
        final int ixL;
        Map<Integer, Perturbation> members;
        int ixMax;
        float absDistsSum;
        private PerturbationSet(int ixL, Map<Integer, Perturbation> members, int ixMax, float absDistsSum) {
            this.ixL = ixL;
            this.members = members;
            this.ixMax = ixMax;
            this.absDistsSum = absDistsSum;
        }

        public static PerturbationSet single(Perturbation p) {
            return new PerturbationSet(p.ixL, new HashMap<Integer, Perturbation>() {{ put(p.ixk, p); }}, 0, p.absDistance);
        }

        public static PerturbationSet shift(Perturbation[] candidates, PerturbationSet pset) {
            if (pset.ixMax + 1 == candidates.length) return null;
            else {
                Perturbation currMax = candidates[pset.ixMax];
                Perturbation nextMax = candidates[pset.ixMax + 1];
                HashMap<Integer, Perturbation> nextMembers = new HashMap<Integer, Perturbation>(pset.members) {{
                    remove(currMax.ixk);
                    put(nextMax.ixk, nextMax);
                }};
                PerturbationSet nextPset = new PerturbationSet(
                        pset.ixL,
                        nextMembers,
                        pset.ixMax + 1,
                        pset.absDistsSum - currMax.absDistance + nextMax.absDistance);
                // In some cases shifting can create an invalid pset, where there are two perturbations for the same index.
                // In that case, call shift recursively to get rid of the invalid pair of perturbations.
                if (pset.members.containsKey(nextMax.ixk) && currMax.ixk != nextMax.ixk) return shift(candidates, nextPset);
                else return nextPset;
            }
        }

        public static PerturbationSet expand(Perturbation[] candidates, PerturbationSet pset) {
            if (pset.ixMax + 1 == candidates.length) return null;
            else {
                Perturbation nextMax = candidates[pset.ixMax + 1];
                HashMap<Integer, Perturbation> nextMembers = new HashMap<Integer, Perturbation>(pset.members) {{
                    put(nextMax.ixk, nextMax);
                }};
                PerturbationSet nextPset = new PerturbationSet(
                        pset.ixL,
                        nextMembers,
                        pset.ixMax + 1,
                        pset.absDistsSum + nextMax.absDistance
                );
                // Sometimes expanding creates an invalid pset. Shifting cleans it up.
                if (pset.members.containsKey(nextMax.ixk)) return shift(candidates, nextPset);
                else return nextPset;
            }
        }
    }

}
