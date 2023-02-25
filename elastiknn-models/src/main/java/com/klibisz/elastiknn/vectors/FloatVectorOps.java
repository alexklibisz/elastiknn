package com.klibisz.elastiknn.vectors;

public interface FloatVectorOps {

    float dotProduct(final float[] v1, final float[] v2);

    double euclideanDistance(final float[] v1, final float[] v2);

}
