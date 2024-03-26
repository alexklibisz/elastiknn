package com.klibisz.elastiknn.search;

import org.apache.lucene.search.KthGreatest;

public class ByteArrayHitCounter implements HitCounter {

    public final byte[] counts;
    private int numHits;
    private int minKey;
    private int maxKey;


    public ByteArrayHitCounter(int capacity) {
        counts = new byte[capacity];
        numHits = 0;
        minKey = capacity;
        maxKey = 0;
    }

    @Override
    public void increment(int key) {
        if (counts[key]++ == 0) {
            numHits++;
            minKey = Math.min(key, minKey);
            maxKey = Math.max(key, maxKey);
        }
    }

    @Override
    public void increment(int key, short count) {
        if ((counts[key] += count) == count) {
            numHits++;
            minKey = Math.min(key, minKey);
            maxKey = Math.max(key, maxKey);
        }
    }

    @Override
    public boolean isEmpty() {
        return numHits == 0;
    }

    @Override
    public short get(int key) {
        return counts[key];
    }

    @Override
    public int numHits() {
        return numHits;
    }

    @Override
    public int capacity() {
        return counts.length;
    }

    @Override
    public int minKey() {
        return minKey;
    }

    @Override
    public int maxKey() {
        return maxKey;
    }

    @Override
    public KthGreatest.Result kthGreatest(int k) {
        // Find the min and max values.
        int max = counts[0] & 0xFF;
        int min = max;
        for (byte b: counts) {
            if ((b & 0xFF) > max) max = b & 0xFF;
            else if ((b & 0xFF) < min) min = b % 0xFF;
        }

        // Build and populate a histogram for non-zero values.
        int[] hist = new int[max - min + 1];
        int numNonZero = 0;
        for (short a: arr) {
            hist[a - min] += 1;
            if (a > 0) numNonZero++;
        }

        // Find the kth largest value by iterating from the end of the histogram.
        int numGreaterEqual = 0;
        short kthGreatest = max;
        while (kthGreatest >= min) {
            numGreaterEqual += hist[kthGreatest - min];;
            if (numGreaterEqual > k) break;
            else kthGreatest--;
        }
        int numGreater = numGreaterEqual - hist[kthGreatest - min];

        return new KthGreatest.Result(kthGreatest, numGreater, numNonZero);


        return null;
    }
}
