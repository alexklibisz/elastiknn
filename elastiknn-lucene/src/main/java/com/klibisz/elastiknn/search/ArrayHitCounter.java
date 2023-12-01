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
        // Find the kth greatest document hit count in O(n) time and O(n) space.
        // Though the space is typically negligibly small in practice.
        // This implementation exploits the fact that we're specifically counting document hit counts.
        // Counts are integers, and they're likely to be pretty small, since we're unlikely to match
        // the same document many times.

        // Start by building a histogram of all counts.
        // e.g., if the counts are [0, 4, 1, 1, 2],
        // then the histogram is [1, 2, 1, 0, 1],
        // because 0 occurs once, 1 occurs twice, 2 occurs once, 3 occurs zero times, and 4 occurs once.
        short[] hist = new short[maxValue + 1];
        for (short c: counts) hist[c]++;

        // Now we start at the max value and iterate backwards through the histogram,
        // accumulating counts of counts until we've exceeded k.
        int numGreaterEqual = 0;
        short kthGreatest = maxValue;
        while (kthGreatest > 0) {
            numGreaterEqual += hist[kthGreatest];
            if (numGreaterEqual > k) break;
            else kthGreatest--;
        }

        // Finally we find the number that were greater than the kth greatest count.
        // There's a special case if kthGreatest is zero, then the number that were greater is the number of hits.
        int numGreater = numGreaterEqual - hist[kthGreatest];
        if (kthGreatest == 0) numGreater = numHits;
        return new KthGreatestResult(kthGreatest, numGreater, numHits);
    }
}
