package com.klibisz.elastiknn.search;

/**
 * Use an array of counts to count hits. The index of the array is the doc id.
 * Hopefully there's a way to do this that doesn't require O(num docs in segment) time and memory,
 * but so far I haven't found anything on the JVM that's faster than simple arrays of primitives.
 */
public class ArrayHitCounter implements HitCounter {

    private final short[] counts;
    private int numHits;
    private int minKey;
    private int maxKey;

    private short maxValue;

    public ArrayHitCounter(int capacity) {
        counts = new short[capacity];
        numHits = 0;
        minKey = capacity;
        maxKey = 0;
        maxValue = 0;
    }

    @Override
    public void increment(int key) {
        short after = ++counts[key];
        if (after == 1) {
            numHits++;
            minKey = Math.min(key, minKey);
            maxKey = Math.max(key, maxKey);
        }
        if (after > maxValue) maxValue = after;
    }

    @Override
    public void increment(int key, short count) {
        short after = (counts[key] += count);
        if (after == count) {
            numHits++;
            minKey = Math.min(key, minKey);
            maxKey = Math.max(key, maxKey);
        }
        if (after > maxValue) maxValue = after;
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
    public KthGreatestResult kthGreatest(int k) {
        // Build and populate a histogram of all counts.
        short[] hist = new short[maxValue + 1];
        for (short c: counts) hist[c]++;

        // Find the kth largest value by iterating from the end of the histogram.
        int numGreaterEqual = 0;
        short kthGreatest = maxValue;
        while (kthGreatest > 0) {
            numGreaterEqual += hist[kthGreatest];
            if (numGreaterEqual > k) break;
            else kthGreatest--;
        }

        // Find the number that were greater than the kth greatest count.
        int numGreater = numHits;
        if (kthGreatest > 0) numGreater = numGreaterEqual - hist[kthGreatest];
        return new KthGreatestResult(kthGreatest, numGreater, numHits);
    }
}
