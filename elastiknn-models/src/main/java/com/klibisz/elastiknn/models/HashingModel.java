package com.klibisz.elastiknn.models;

public class HashingModel {

    public interface SparseBool {
        HashAndFreq[] hash(int[] trueIndices, int totalIndices);
    }

    public interface DenseFloat {
        HashAndFreq[] hash(float[] values);

        static float dot(float[] v1, float[] v2) {
            float dp = 0f;
            for (int i = 0; i < v1.length; i++) dp += v1[i] * v2[i];
            return dp;
        }

    }


}
