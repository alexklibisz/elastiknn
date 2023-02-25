package com.klibisz.elastiknn.vectors;

public interface FloatVectorOps {

    double dotProduct(final float[] v1, final float[] v2);

    double l2Distance(final float[] v1, final float[] v2);

    double l1Distance(float[] v1, float[] v2);
}
