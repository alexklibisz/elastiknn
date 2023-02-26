package com.klibisz.elastiknn.vectors;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Arrays;

public final class PanamaFloatVectorOps implements FloatVectorOps {

    private final VectorSpecies<Float> species = FloatVector.SPECIES_PREFERRED;

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
        if (i < v1.length) {
            VectorMask<Float> m = species.indexInRange(i, v1.length);
            pv1 = FloatVector.fromArray(species, v1, i, m);
            pv2 = FloatVector.fromArray(species, v2, i, m);
            dotProd += pv1.mul(pv2).reduceLanes(VectorOperators.ADD);
            v1SqrSum += pv1.mul(pv1).reduceLanes(VectorOperators.ADD);
            v2SqrSum += pv2.mul(pv2).reduceLanes(VectorOperators.ADD);
        }
        double denom = Math.sqrt(v1SqrSum) * Math.sqrt(v2SqrSum);
        if (denom > 0) return dotProd / denom;
        else if (Arrays.equals(v1, v2)) return 1;
        else return -1;
    }
    public double cosineSimilarityTailLoop(float[] v1, float[] v2) {
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
            dotProd = Math.fma(v1[i], v2[i], dotProd);
            v1SqrSum = Math.fma(v1[i], v1[i], v1SqrSum);
            v2SqrSum = Math.fma(v2[i], v2[i], v2SqrSum);
        }
        double denom = Math.sqrt(v1SqrSum) * Math.sqrt(v2SqrSum);
        if (denom > 0) return dotProd / denom;
        else if (Arrays.equals(v1, v2)) return 1;
        else return -1;
    }

    public double dotProduct(float[] v1, float[] v2) {
        double dp = 0f;
        int i = 0;
        int bound = species.loopBound(v1.length);
        FloatVector pv1, pv2;
        for (; i < bound; i += species.length()) {
            pv1 = FloatVector.fromArray(species, v1, i);
            pv2 = FloatVector.fromArray(species, v2, i);
            dp += pv1.mul(pv2).reduceLanes(VectorOperators.ADD);
        }
        if (i == v1.length) return dp;
        else {
            VectorMask<Float> m = species.indexInRange(i, v1.length);
            pv1 = FloatVector.fromArray(species, v1, i, m);
            pv2 = FloatVector.fromArray(species, v2, i, m);
            return dp + pv1.mul(pv2).reduceLanes(VectorOperators.ADD);
        }
    }

    public double dotProductTailLoop(float[] v1, float[] v2) {
        double dp = 0f;
        int i = 0;
        int bound = species.loopBound(v1.length);
        FloatVector pv1, pv2;
        for (; i < bound; i += species.length()) {
            pv1 = FloatVector.fromArray(species, v1, i);
            pv2 = FloatVector.fromArray(species, v2, i);
            dp += pv1.mul(pv2).reduceLanes(VectorOperators.ADD);
        }
        for (; i < v1.length; i++) {
            dp = Math.fma(v1[i], v2[i], dp);
        }
        return dp;
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
        if (i < v1.length) {
            VectorMask<Float> m = species.indexInRange(i, v1.length);
            pv1 = FloatVector.fromArray(species, v1, i, m);
            pv2 = FloatVector.fromArray(species, v2, i, m);
            sumAbsDiff += pv1.sub(pv2).abs().reduceLanes(VectorOperators.ADD);
        }
        return sumAbsDiff;
    }

    public double l1DistanceTailLoop(float[] v1, float[] v2) {
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
        if (i < v1.length) {
            VectorMask<Float> m = species.indexInRange(i, v1.length);
            pv1 = FloatVector.fromArray(species, v1, i, m);
            pv2 = FloatVector.fromArray(species, v2, i, m);
            pv3 = pv1.sub(pv2);
            sumSqrDiff += pv3.mul(pv3).reduceLanes(VectorOperators.ADD);
        }
        return Math.sqrt(sumSqrDiff);
    }

    public double l2DistanceTailLoop(float[] v1, float[] v2) {
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
            sumSqrDiff += Math.pow(v1[i] - v2[i], 2);
        }
        return Math.sqrt(sumSqrDiff);
    }
}
