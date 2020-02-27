package com.klibisz.elastiknn.models;

import java.util.Random;

public class JaccardLshModel {

    private final int numTables;
    private final int numBands;
    private final int numRows;
    private final int[] alphas;
    private final int[] betas;
    private static long HASH_PRIME = 2038074743;

    public JaccardLshModel(long seed, int numTables, int numBands, int numRows) {
        this.numTables = numTables;
        this.numBands = numBands;
        this.numRows = numRows;

        Random rng = new Random(seed);
        this.alphas = new int[numTables * numBands * numRows];
        this.betas = new int[numTables * numBands * numRows];
        for(int i = 0; i < alphas.length; i++) {
            alphas[i] = 1 + rng.nextInt(Long.valueOf(HASH_PRIME).intValue() - 1);
            betas[i] = rng.nextInt(Long.valueOf(HASH_PRIME).intValue() - 1);
        }
    }

    private static long tableBandHash(int table, int band, long bandHash) {
        return ((((table % HASH_PRIME) + band) % HASH_PRIME) + bandHash) % HASH_PRIME;
    }

    long[] getEmptyHashes() {
        long[] emptyHashes = new long[numTables * numBands];
        long bandHash = 0;
        for (int r = 0; r < numRows; r++) {
            bandHash = (bandHash + Long.MAX_VALUE) % HASH_PRIME;
        }
        for (int t = 0; t < numTables; t++) {
            for (int b = 0; b < numBands; b++) {
                emptyHashes[t * numBands + b] = tableBandHash(t, b, bandHash);
            }
        }
        return emptyHashes;
    }


    long[] hash(int[] trueIndices) {
        if (trueIndices.length == 0) {
            return getEmptyHashes();
        } else {
            long[] tableBandHashes = new long[numTables * numBands];
            int ixHashes = 0;
            int ixCoefs = 0;
            for (int t = 0; t < numTables; t++) {
                for (int b = 0; b < numBands; b++) {
                    long bandHash = 0;
                    for (int r = 0; r < numRows; r++) {
                        long rowHash = Long.MAX_VALUE;
                        for (int ixTrue : trueIndices) {
                            long h = ((1L + ixTrue) * alphas[ixCoefs] + betas[ixCoefs]) % HASH_PRIME;
                            rowHash = Math.min(h, rowHash);
                        }
                        bandHash = (bandHash + rowHash) % HASH_PRIME;
                        ixCoefs += 1;
                    }
                    tableBandHashes[ixHashes] = tableBandHash(t, b, bandHash);
                    ixHashes += 1;
                }
            }
            return tableBandHashes;
        }
    }


}