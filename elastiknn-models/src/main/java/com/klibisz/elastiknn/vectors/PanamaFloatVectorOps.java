package com.klibisz.elastiknn.vectors;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Arrays;

public final class PanamaFloatVectorOps implements FloatVectorOps {

    final VectorSpecies<Float> species = FloatVector.SPECIES_PREFERRED;

    private final FloatVectorOps defaultFloatVectorOps = new DefaultFloatVectorOps();

    public double cosineSimilarity(float[] v1, float[] v2) {
        double dotProd = 0.0;
        double v1SqrSum = 0.0;
        double v2SqrSum = 0.0;
        int i = 0;
        int bound = species.loopBound(v1.length);
        FloatVector pv1, pv2;
        for (; i < bound; i += species.length()) {
            pv1 = FloatVector.fromArray(species, v1, i);
            pv2 = FloatVector.fromArray(species, v2, i);
            dotProd += pv1.mul(pv2).reduceLanes(VectorOperators.ADD);
            v1SqrSum += pv1.mul(pv1).reduceLanes(VectorOperators.ADD);
            v2SqrSum += pv2.mul(pv2).reduceLanes(VectorOperators.ADD);
        }
        for (; i < v1.length; i++) {
            dotProd += v1[i] * v2[i];
            v1SqrSum += v1[i] * v1[i];
            v2SqrSum += v2[i] * v2[i];
        }
        double denom = Math.sqrt(v1SqrSum) * Math.sqrt(v2SqrSum);
        if (denom > 0) return dotProd / denom;
        else if (Arrays.equals(v1, v2)) return 1;
        else return -1;
    }

    public double dotProduct(float[] v1, float[] v2) {
        double dotProd = 0f;
        int i = 0;
        int bound = species.loopBound(v1.length);
        FloatVector pv1, pv2;
        for (; i < bound; i += species.length()) {
            pv1 = FloatVector.fromArray(species, v1, i);
            pv2 = FloatVector.fromArray(species, v2, i);
            dotProd += pv1.mul(pv2).reduceLanes(VectorOperators.ADD);
        }
        for (; i < v1.length; i++) {
            dotProd += v1[i] * v2[i];
        }
        return dotProd;
    }


    public double l1Distance(float[] v1, float[] v2) {
        double sumAbsDiff = 0.0;
        int i = 0;
        int bound = species.loopBound(v1.length);
        FloatVector pv1, pv2;
        for (; i < bound; i += species.length()) {
            pv1 = FloatVector.fromArray(species, v1, i);
            pv2 = FloatVector.fromArray(species, v2, i);
            sumAbsDiff += pv1.sub(pv2).abs().reduceLanes(VectorOperators.ADD);
        }
        for (; i < v1.length; i++) {
            sumAbsDiff += Math.abs(v1[i] - v2[i]);
        }
        return sumAbsDiff;
    }

    public double l2Distance(float[] v1, float[] v2) {
        double sumSqrDiff = 0f;
        int i = 0;
        int bound = species.loopBound(v1.length);
        FloatVector pv1, pv2, pv3;
        for (; i < bound; i+= species.length()) {
            pv1 = FloatVector.fromArray(species, v1, i);
            pv2 = FloatVector.fromArray(species, v2, i);
            pv3 = pv1.sub(pv2);
            // For some unknown reason, pv3.mul(pv3) is significantly faster than pv3.pow(2).
            sumSqrDiff += pv3.mul(pv3).reduceLanes(VectorOperators.ADD);
        }
        for (; i < v1.length; i++) {
            float diff = v1[i] - v2[i];
            sumSqrDiff += diff * diff;
        }
        return Math.sqrt(sumSqrDiff);
    }


    public int[][] l2LshHash(int L, int k, int w, float[][] A, float[] B, float[] vec) {
        FloatVector pv1, pv2;
        int bound = species.loopBound(vec.length);
        int[][] hashes = new int[L][k];
        for (int ixL = 0; ixL < L; ixL++) {
            for (int ixk = 0; ixk < k; ixk++) {
                float[] a = A[ixL * k + ixk];
                float b = B[ixL * k + ixk];
                double dotProd = 0f;
                int i = 0;
                for (; i < bound; i += species.length()) {
                    pv1 = FloatVector.fromArray(species, a, i);
                    pv2 = FloatVector.fromArray(species, vec, i);
                    dotProd += pv1.mul(pv2).reduceLanes(VectorOperators.ADD);
                }
                for (; i < vec.length; i++) {
                    dotProd += a[i] * vec[i];
                }
                hashes[ixL][ixk] = (int) Math.floor((dotProd + b) / w);
            }
        }
        return hashes;
    }
}
