package com.klibisz.elastiknn.search;

/**
 * Use an array of counts to count hits. The index of the array is the doc id.
 * Hopefully there's a way to do this that doesn't require O(num docs in segment) time and memory,
 * but so far I haven't found anything on the JVM that's faster than simple arrays of primitives.
 */
public class ArrayHitCounter implements HitCounter {

    private final short[] counts;
    private boolean isEmpty;

    public ArrayHitCounter(int maxDocs) {
        counts = new short[maxDocs];
        isEmpty = true;
    }

    @Override
    public void increment(int key, short count) {
        counts[key] += count;
        isEmpty = false;
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
    public int numHits() {
        return counts.length;
    }

    @Override
    public KthGreatest.Result kthGreatest(int k) {
        return KthGreatest.kthGreatest(counts, Math.min(k, counts.length - 1));
    }

    @Override
    public Iterator iterator() {
        return new Iterator() {

            private int i = -1;

            @Override
            public void advance() {
                i++;
            }

            @Override
            public boolean hasNext() {
                return i + 1 < counts.length;
            }

            @Override
            public int docID() {
                return i;
            }

            @Override
            public int count() {
                return counts[i];
            }
        };
    }
}
