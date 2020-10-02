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
    private boolean isEmpty;

    public ArrayHitCounter(int maxDocs) {
        counts = new short[maxDocs];
        isEmpty = true;
    }

    @Override
    public short increment(int key, short count) {
        counts[key] += count;
        isEmpty = false;
        if (counts[key] == 1) numHits++;
        minKey = Math.min(minKey, key);
        maxKey = Math.max(maxKey, key);
        return counts[key];
    }

    @Override
    public short increment(int key, int count) {
        return increment(key, (short) count);
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

    @Override
    public int minKey() {
        return this.minKey;
    }

    @Override
    public int maxKey() {
        return this.maxKey;
    }

}
