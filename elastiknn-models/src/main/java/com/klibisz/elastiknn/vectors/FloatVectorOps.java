package com.klibisz.elastiknn.vectors;

public interface FloatVectorOps {

    double dotProduct(float[] v1, float[] v2);

    double l2Distance(float[] v1, float[] v2);

    double l1Distance(float[] v1, float[] v2);

    double cosineSimilarity(float[] v1, float[] v2);

    double dotSimilarity(float[] v1, float[] v2);
}
