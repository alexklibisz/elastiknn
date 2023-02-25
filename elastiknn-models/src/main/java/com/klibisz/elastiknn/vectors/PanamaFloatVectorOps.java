package com.klibisz.elastiknn.vectors;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public final class PanamaFloatVectorOps implements FloatVectorOps {

    private final VectorSpecies<Float> species = FloatVector.SPECIES_PREFERRED;

    @Override
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
}
