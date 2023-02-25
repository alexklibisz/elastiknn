package com.klibisz.elastiknn.models;

import com.klibisz.elastiknn.vectors.BooleanVectorOps;
import com.klibisz.elastiknn.vectors.FloatVectorOps;
import jdk.internal.vm.annotation.ForceInline;

import java.util.Arrays;

public class ExactModel {

    public static class Jaccard {
        @ForceInline
        public double similarity(int[] v1, int[] v2, int totalIndices) {
            int isec = BooleanVectorOps.sortedIntersectionCount(v1, v2);
            int denom = v1.length + v2.length - isec;
            if (isec == 0 && denom == 0) return 1;
            else if (denom > 0) return isec * 1.0 / denom;
            else return 0;
        }
    }

    public static class Hamming {
        @ForceInline
        public double similarity(int[] v1, int[] v2, int totalIndices) {
            int eqTrueCount = BooleanVectorOps.sortedIntersectionCount(v1, v2);
            int neqTrueCount = Math.max(v1.length - eqTrueCount, 0) + Math.max(v2.length - eqTrueCount, 0);
            return (totalIndices - neqTrueCount) * 1d / totalIndices;
        }
    }

    public static class L2 {

        @ForceInline
        public double similarity(FloatVectorOps floatVectorOps, float[] v1, float[] v2) {
            return 1.0 / (1 + floatVectorOps.l2Distance(v1, v2));
        }
    }

    public static class L1 {
        @ForceInline
        public double similarity(FloatVectorOps floatVectorOps, float[] v1, float[] v2) {
            return 1.0 / (1 + floatVectorOps.l1Distance(v1, v2));
        }
    }

    public static class Cosine {
        @ForceInline
        public double similarity(float[] v1, float[] v2) {
            double dotProd = 0.0;
            double v1SqrSum = 0.0;
            double v2SqrSum = 0.0;
            for (int i = 0; i < v1.length; i++) {
                dotProd += v1[i] * v2[i];
                v1SqrSum += Math.pow(v1[i], 2);
                v2SqrSum += Math.pow(v2[i], 2);
            }
            double denom = Math.sqrt(v1SqrSum) * Math.sqrt(v2SqrSum);
            if (denom > 0) return 1 + (dotProd / denom);
            else if (Arrays.equals(v1, v2)) return 2;
            else return 0;
        }
    }

}
