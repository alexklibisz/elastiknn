package com.klibisz.elastiknn.search;

import org.apache.lucene.search.KthGreatest;

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

    public ArrayHitCounter(int capacity) {
        counts = new short[capacity];
        numHits = 0;
        minKey = capacity;
        maxKey = 0;
    }

    @Override
    public void increment(int key) {
        // Increment counts[key] and check if the original value was zero.
        // If it was, we have a new hit and we need to do some bookkeeping.
        if (counts[key]++ == 0) {
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
        return KthGreatest.kthGreatest(counts, Math.min(k, counts.length - 1));
    }

}
