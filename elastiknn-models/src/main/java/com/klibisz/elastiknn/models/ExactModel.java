package com.klibisz.elastiknn.models;

import com.klibisz.elastiknn.vectors.BooleanVectorOps;
import com.klibisz.elastiknn.vectors.FloatVectorOps;
import jdk.internal.vm.annotation.ForceInline;

import java.util.Arrays;

public class ExactModel {

    @ForceInline
    public static double jaccardSimilarity(int[] v1, int[] v2, int totalIndices) {
        int isec = BooleanVectorOps.sortedIntersectionCount(v1, v2);
        int denom = v1.length + v2.length - isec;
        if (isec == 0 && denom == 0) return 1;
        else if (denom > 0) return isec * 1.0 / denom;
        else return 0;
    }

    @ForceInline
    public static double hammingSimilarity(int[] v1, int[] v2, int totalIndices) {
        int eqTrueCount = BooleanVectorOps.sortedIntersectionCount(v1, v2);
        int neqTrueCount = Math.max(v1.length - eqTrueCount, 0) + Math.max(v2.length - eqTrueCount, 0);
        return (totalIndices - neqTrueCount) * 1d / totalIndices;
    }

    @ForceInline
    public static double l2Similarity(FloatVectorOps floatVectorOps, float[] v1, float[] v2) {
        return 1.0 / (1 + floatVectorOps.l2Distance(v1, v2));
    }

    @ForceInline
    public static double l1Similarity(FloatVectorOps floatVectorOps, float[] v1, float[] v2) {
        return 1.0 / (1 + floatVectorOps.l1Distance(v1, v2));
    }

    @ForceInline
    public static double cosineSimilarity(FloatVectorOps floatVectorOps, float[] v1, float[] v2) {
        return 1 + floatVectorOps.cosineSimilarity(v1, v2);
    }
}
