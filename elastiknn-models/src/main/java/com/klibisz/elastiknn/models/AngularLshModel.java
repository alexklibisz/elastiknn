package com.klibisz.elastiknn.models;

public class AngularLshModel implements HashingModel.DenseFloat {

    final int dims;
    final int L;
    final int k;

    public AngularLshModel(int dims, int L, int k) {
        this.dims = dims;
        this.L = L;
        this.k = k;
    }

    @Override
    public HashAndFreq[] hash(float[] values) {




        return new HashAndFreq[0];
    }
}
