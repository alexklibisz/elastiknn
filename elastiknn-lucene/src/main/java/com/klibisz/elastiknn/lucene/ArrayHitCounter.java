package com.klibisz.elastiknn.lucene;

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
    public KthGreatest kthGreatest(int k) {

        // Find the min and max values.
        short max = counts[0];
        short min = counts[0];
        for (short c: counts) {
            if (c > max) max = c;
            else if (c < min) min = c;
        }

        // Build and populate a histogram for non-zero values.
        int[] hist = new int[max - min + 1];
        for (short c: counts) {
            hist[c - min] += 1;
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

        return new KthGreatest(kthGreatest, numGreater);
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
