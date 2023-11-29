package com.klibisz.elastiknn.models;

import com.klibisz.elastiknn.storage.BitBuffer;
import com.klibisz.elastiknn.vectors.FloatVectorOps;

import static com.klibisz.elastiknn.storage.UnsafeSerialization.writeInt;

import java.util.Random;

public class CosineLshModel implements HashingModel.DenseFloat {

    private final int L;
    private final int k;
    private final float[][] planes;

    private final FloatVectorOps vectorOps;

    /**
     * Locality sensitive hashing model for Cosine similarity.
     * Uses the random hyperplanes method described in Mining Massive Datasets chapter 3.
     * @param dims length of the vectors hashed by this model
     * @param L number of hash tables
     * @param k number of hash functions concatenated to form a hash for each table
     * @param rng random number generator used to instantiate model parameters
     */
    public CosineLshModel(int dims, int L, int k, Random rng, FloatVectorOps vectorOps) {
        this.L = L;
        this.k = k;
        this.planes = new float[L * k][dims];
        this.vectorOps = vectorOps;
        for (int i = 0; i < this.planes.length; i++) {
            for (int j = 0; j < dims; j++) {
                this.planes[i][j] = (float) rng.nextGaussian();
            }
        }
    }

    @Override
    public byte[][] hash(float[] values) {
        byte[][] hashes = new byte[L][];
        for (int ixL = 0; ixL < L; ixL++) {
            BitBuffer.IntBuffer buf = new BitBuffer.IntBuffer(writeInt(ixL));
            for (int ixk = 0; ixk < k; ixk++) {
                double dot = vectorOps.dotProduct(planes[ixL * k + ixk], values);
                if (dot > 0) buf.putOne();
                else buf.putZero();
            }
            hashes[ixL] = buf.toByteArray();
        }
        return hashes;
    }

}
