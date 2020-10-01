package com.klibisz.elastiknn.search;

/**
 * Use an array of counts to count hits. The index of the array is the doc id.
 * Hopefully there's a way to do this that doesn't require O(num docs in segment) time and memory,
 * but so far I haven't found anything on the JVM that's faster than simple arrays of primitives.
 */
public class ArrayHitCounter implements HitCounter {

    private final short[] counts;
    private final boolean[] hits;
    private int numHits;
    private boolean isEmpty;

    public ArrayHitCounter(int maxDocs) {
        counts = new short[maxDocs];
        hits = new boolean[maxDocs];
        isEmpty = true;
    }

    @Override
    public void increment(int key, short count) {
        counts[key] += count;
        isEmpty = false;
        if (!hits[key]) {
            hits[key] = true;
            numHits++;
        }
    }

    @Override
    public void increment(int key, int count) {
        increment(key, (short) count);
    }

    @Override
    public boolean isEmpty() {
        return isEmpty;
    }

    @Override
    public short get(int key) {
        return counts[key];
    }

    @Override
    public int capacity() {
        return counts.length;
    }

    @Override
    public int hits() {
        return numHits;
    }

}
