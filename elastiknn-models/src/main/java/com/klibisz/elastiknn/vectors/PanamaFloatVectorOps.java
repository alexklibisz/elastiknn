package com.klibisz.elastiknn.vectors;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.Arrays;

public final class PanamaFloatVectorOps implements FloatVectorOps {

    private static final VectorSpecies<Float> species;
    private static final VectorSpecies<Float> PREF_FLOAT_SPECIES;

    static {
        species = FloatVector.SPECIES_PREFERRED;
        PREF_FLOAT_SPECIES = FloatVector.SPECIES_PREFERRED;
    }

    final int speciesLength = species.length();
    final int speciesLengthTimes2 = speciesLength * 2;
    final int speciesLengthTimes3 = speciesLength * 3;
    final int speciesLengthTimes4 = speciesLength * 4;

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
        int i = 0;
        double dotProd = 0d;
        if (v1.length > speciesLengthTimes2) {
            int bound;
            FloatVector acc1 = FloatVector.zero(species);
            FloatVector acc2 = FloatVector.zero(species);
            FloatVector acc3 = FloatVector.zero(species);
            FloatVector acc4 = FloatVector.zero(species);
            FloatVector fv1, fv2;
            for (bound = species.loopBound(v1.length - 3 * speciesLength); i < bound; i += speciesLengthTimes4) {
                fv1 = FloatVector.fromArray(species, v1, i);
                fv2 = FloatVector.fromArray(species, v2, i);
                acc1 = acc1.add(fv1.mul(fv2));
                FloatVector fv3 = FloatVector.fromArray(species, v1, i + speciesLength);
                FloatVector fv4 = FloatVector.fromArray(species, v2, i + speciesLength);
                acc2 = acc2.add(fv3.mul(fv4));
                FloatVector fv5 = FloatVector.fromArray(species, v1, i + speciesLengthTimes2);
                FloatVector fv6 = FloatVector.fromArray(species, v2, i + speciesLengthTimes2);
                acc3 = acc3.add(fv5.mul(fv6));
                FloatVector fv7 = FloatVector.fromArray(species, v1, i + speciesLengthTimes3);
                FloatVector fv8 = FloatVector.fromArray(species, v2, i + speciesLengthTimes3);
                acc4 = acc4.add(fv7.mul(fv8));
            }
            for (bound = species.loopBound(v1.length); i < bound; i += speciesLength) {
                fv1 = FloatVector.fromArray(species, v1, i);
                fv2 = FloatVector.fromArray(species, v2, i);
                acc1 = acc1.add(fv1.mul(fv2));
            }
            fv1 = acc1.add(acc2);
            fv2 = acc3.add(acc4);
            dotProd += fv1.add(fv2).reduceLanes(VectorOperators.ADD);
        }
        while (i < v1.length) {
            dotProd += v1[i] * v2[i];
            ++i;
        }
        return dotProd;
    }

    public double dotProductOriginal(float[] v1, float[] v2) {
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

//    public float dotProductLucene(float[] a, float[] b) {
//        int i = 0;
//        float res = 0.0F;
//        if (a.length > 2 * PREF_FLOAT_SPECIES.length()) {
//            FloatVector acc1 = FloatVector.zero(PREF_FLOAT_SPECIES);
//            FloatVector acc2 = FloatVector.zero(PREF_FLOAT_SPECIES);
//            FloatVector acc3 = FloatVector.zero(PREF_FLOAT_SPECIES);
//            FloatVector acc4 = FloatVector.zero(PREF_FLOAT_SPECIES);
//
//            int upperBound;
//            FloatVector res1;
//            FloatVector res2;
//            for(upperBound = PREF_FLOAT_SPECIES.loopBound(a.length - 3 * PREF_FLOAT_SPECIES.length()); i < upperBound; i += 4 * PREF_FLOAT_SPECIES.length()) {
//                res1 = FloatVector.fromArray(PREF_FLOAT_SPECIES, a, i);
//                res2 = FloatVector.fromArray(PREF_FLOAT_SPECIES, b, i);
//                acc1 = acc1.add(res1.mul(res2));
//                FloatVector vc = FloatVector.fromArray(PREF_FLOAT_SPECIES, a, i + PREF_FLOAT_SPECIES.length());
//                FloatVector vd = FloatVector.fromArray(PREF_FLOAT_SPECIES, b, i + PREF_FLOAT_SPECIES.length());
//                acc2 = acc2.add(vc.mul(vd));
//                FloatVector ve = FloatVector.fromArray(PREF_FLOAT_SPECIES, a, i + 2 * PREF_FLOAT_SPECIES.length());
//                FloatVector vf = FloatVector.fromArray(PREF_FLOAT_SPECIES, b, i + 2 * PREF_FLOAT_SPECIES.length());
//                acc3 = acc3.add(ve.mul(vf));
//                FloatVector vg = FloatVector.fromArray(PREF_FLOAT_SPECIES, a, i + 3 * PREF_FLOAT_SPECIES.length());
//                FloatVector vh = FloatVector.fromArray(PREF_FLOAT_SPECIES, b, i + 3 * PREF_FLOAT_SPECIES.length());
//                acc4 = acc4.add(vg.mul(vh));
//            }
//
//            for(upperBound = PREF_FLOAT_SPECIES.loopBound(a.length); i < upperBound; i += PREF_FLOAT_SPECIES.length()) {
//                res1 = FloatVector.fromArray(PREF_FLOAT_SPECIES, a, i);
//                res2 = FloatVector.fromArray(PREF_FLOAT_SPECIES, b, i);
//                acc1 = acc1.add(res1.mul(res2));
//            }
//
//            res1 = acc1.add(acc2);
//            res2 = acc3.add(acc4);
//            res += res1.add(res2).reduceLanes(VectorOperators.ADD);
//        }
//
//        while(i < a.length) {
//            res += b[i] * a[i];
//            ++i;
//        }
//
//        return res;
//    }

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
}
