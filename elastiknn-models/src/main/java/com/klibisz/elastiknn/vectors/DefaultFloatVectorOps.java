package com.klibisz.elastiknn.vectors;

import java.util.Arrays;

public final class DefaultFloatVectorOps implements FloatVectorOps {

    public double dotProduct(float[] v1, float[] v2) {
        float dp = 0f;
        for (int i = 0; i < v1.length; i++) dp = Math.fma(v1[i], v2[i], dp);
        return dp;
    }

    public double l2Distance(float[] v1, float[] v2) {
        double sumSqrDiff = 0.0;
        for (int i = 0; i < v1.length; i++) {
            sumSqrDiff += Math.pow(v1[i] - v2[i], 2);
        }
        return Math.sqrt(sumSqrDiff);
    }

    public double l1Distance(float[] v1, float[] v2) {
        double sumAbsDiff = 0.0;
        for (int i = 0; i < v1.length; i++) {
            sumAbsDiff += Math.abs(v1[i] - v2[i]);
        }
        return sumAbsDiff;
    }

    public double cosineSimilarity(float[] v1, float[] v2) {
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
