package com.klibisz.elastiknn.vectors;

public final class DefaultFloatVectorOps implements FloatVectorOps {

    @Override
    public double dotProduct(float[] v1, float[] v2) {
        float dp = 0f;
        for (int i = 0; i < v1.length; i++) dp = Math.fma(v1[i], v2[i], dp);
        return dp;
    }

    @Override
    public double l2Distance(float[] v1, float[] v2) {
        double sumSqrDiff = 0.0;
        for (int i = 0; i < v1.length; i++) {
            sumSqrDiff += Math.pow(v1[i] - v2[i], 2);
        }
        return Math.sqrt(sumSqrDiff);
    }

    @Override
    public double l1Distance(float[] v1, float[] v2) {
        double sumAbsDiff = 0.0;
        for (int i = 0; i < v1.length; i++) {
            sumAbsDiff += Math.abs(v1[i] - v2[i]);
        }
        return sumAbsDiff;
    }
}
