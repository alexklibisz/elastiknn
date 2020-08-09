package com.klibisz.elastiknn.models;

import com.google.common.cache.LoadingCache;
import com.klibisz.elastiknn.storage.BitBuffer;
import com.klibisz.elastiknn.storage.UnsafeSerialization;

import java.util.Random;

public class AngularLshModel implements HashingModel.DenseFloat {

    private final int L;
    private final int k;
    private final float[][] planes;

    public AngularLshModel(int dims, int L, int k, Random rng) {
        this.L = L;
        this.k = k;
        this.planes = new float[L * k][dims];
        for (int i = 0; i < this.planes.length; i++) {
            for (int j = 0; j < dims; j++) {
                this.planes[i][j] = (float) rng.nextGaussian();
            }
        }
    }

    @Override
    public HashAndFreq[] hash(float[] values) {
        HashAndFreq[] hashes = new HashAndFreq[L];
        for (int ixL = 0; ixL < L; ixL++) {
            BitBuffer.IntBuffer buf = new BitBuffer.IntBuffer(UnsafeSerialization.writeInt(ixL));
            for (int ixk = 0; ixk < k; ixk++) {
                float dot = HashingModel.DenseFloat.dot(planes[ixL * k + ixk], values);
                if (dot > 0) buf.putOne();
                else buf.putZero();
            }
            hashes[ixL] = HashAndFreq.once(buf.toByteArray());
        }
        return hashes;
    }

}
