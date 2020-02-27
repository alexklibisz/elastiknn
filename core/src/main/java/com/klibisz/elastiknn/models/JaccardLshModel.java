package com.klibisz.elastiknn.models;
//
//import scala.util.hashing.MurmurHash3;
//
//import java.util.Objects;
//import java.util.Random;
//
//public class JaccardLshModel {
//
//    private int numTables;
//    private int numBands;
//    private int numRows;
//    private Random rng;
//    private int[] alphas;
//    private int[] betas;
//
//    public JaccardLshModel(long seed, int numTables, int numBands, int numRows) {
//        this.rng = new Random(seed);
//        this.numTables = numTables;
//        this.numBands = numBands;
//        this.numRows = numRows;
//
//        int HASH_PRIME = 2038074743;
//        this.alphas = new int[numTables * numBands * numRows];
//        this.betas = new int[numTables * numBands * numRows];
//        for(int i = 0; i < this.alphas.length; i++) {
//            this.alphas[i] = 1 + rng.nextInt(HASH_PRIME - 1);
//            this.betas[i] = rng.nextInt(HASH_PRIME - 1);
//        }
//    }
//
//    private String[] getEmptyHashes() {
//        String[] emptyHashes = new String[numTables * numBands];
//
//        return null;
//    }
//
//
//
//    String [] hash(int[] trueIndices) {
//
//        if (trueIndices.length == 0)
//
//        String[] hashes = new String[this.numTables * this.numBands];
//        int ixHashes = 0;
//        int ixCoefs = 0;
//
//
//
//
//
//        return null;
//    }
//
//
//}

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.apache.commons.codec.digest.MurmurHash3;

public class JaccardLshModel {
    public static void main(String[] args) {
        int[] data = { 100, 200, 300, 400 };

        ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(data);

        byte[] array = byteBuffer.array();

        for (int i=0; i < array.length; i++) {
            System.out.println(i + ": " + array[i]);
        }

        long hash = MurmurHash3.hash32x86(array, 0, array.length, 0xb592f7ae);
        System.out.println(hash);
    }
}
