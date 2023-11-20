package com.klibisz.elastiknn.vectors;

public interface FloatVectorOps {

    double dotProduct(float[] v1, float[] v2);

    double l2Distance(float[] v1, float[] v2);

    double l1Distance(float[] v1, float[] v2);

    double cosineSimilarity(float[] v1, float[] v2);

    int[][] l2LshHash(int L, int k, int w, float[][] A, float[] B, float[] vec);
}
