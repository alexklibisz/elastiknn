package com.klibisz.elastiknn.models;

import java.util.Arrays;
import java.util.Random;

public class JaccardLshModel {

    private int numTables;
    private int numBands;
    private int numRows;
    private int[] alphas;
    private int[] betas;
    private static long HASH_PRIME = 2038074743;

    public JaccardLshModel(long seed, int numTables, int numBands, int numRows) {
        this.numTables = numTables;
        this.numBands = numBands;
        this.numRows = numRows;

        Random rng = new Random(seed);
        this.alphas = new int[numTables * numBands * numRows];
        this.betas = new int[numTables * numBands * numRows];
        for(int i = 0; i < this.alphas.length; i++) {
            this.alphas[i] = 1 + rng.nextInt(Long.valueOf(HASH_PRIME).intValue() - 1);
            this.betas[i] = rng.nextInt(Long.valueOf(HASH_PRIME).intValue() - 1);
        }
    }

    private static long hashBand(long[] rowHashes) {
        long h = 0;
        for (long hash : rowHashes) {
            h = (h + hash) % HASH_PRIME;
        }
        return h;
    }

    private static String tableBandHash(int t, int b, long h) {
        return t + "," + b + "," + h;
    }

    String[] getEmptyHashes() {
        String[] emptyHashes = new String[numTables * numBands];
        long[] fakeHashes = new long[numRows];
        Arrays.fill(fakeHashes, Long.MAX_VALUE);
        long hash = hashBand(fakeHashes);
        for (int t = 0; t < numTables; t++) {
            for (int b = 0; b < numBands; b++) {
                emptyHashes[t * numBands + b] = tableBandHash(t, b, hash);
            }
        }
        return emptyHashes;
    }


    String[] hash(int[] trueIndices) {

        if (trueIndices.length == 0) {
            return getEmptyHashes();
        } else {
            String[] allHashes = new String[this.numTables * this.numBands];
            long[] rowHashes = new long[numRows];
            int ixHashes = 0;
            int ixCoefs = 0;

            for (int t = 0; t < numTables; t++) {
                for (int b = 0; b < numBands; b++) {
                    for (int r = 0; r < numRows; r++) {
                        long rowHash = Long.MAX_VALUE;
                        for (int ixTrue : trueIndices) {
                            long h = ((1L + ixTrue) * alphas[ixCoefs] + betas[ixCoefs]) % HASH_PRIME;
                            rowHash = Math.min(rowHash, h);
                        }
                        rowHashes[r] = rowHash;
                        ixCoefs += 1;
                    }
                    allHashes[ixHashes] = tableBandHash(t, b, hashBand(rowHashes));
                    ixHashes += 1;
                }
            }

            return allHashes;
        }
    }


}