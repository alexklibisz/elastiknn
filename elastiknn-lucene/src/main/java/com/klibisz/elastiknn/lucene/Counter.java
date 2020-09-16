package com.klibisz.elastiknn.lucene;

public class Counter {

    public final int[] hits;
    public final short[] counts;
    public int numHits;

    public Counter(int maxDoc) {
        this.numHits = 0;
        this.hits = new int[maxDoc];
        this.counts = new short[maxDoc];
    }

    public static class KthGreatest {
        public final short max;
        public final short kthGreatest;
        public final int numGreaterThan;
        public KthGreatest(short max, short kthGreatest, int numGreaterThan) {
            this.max = max;
            this.kthGreatest = kthGreatest;
            this.numGreaterThan = numGreaterThan;
        }
    }

    public void increment(int docId, short count) {
        counts[docId] += count;
        hits[numHits] = docId;
        numHits++;
    }

    public KthGreatest kthGreatest(int k) {

        // Find the min and max values.
        short max = counts[hits[0]];
        short min = counts[hits[0]];
        for (int i = 0; i < numHits; i++) {
            short c = counts[hits[i]];
            if (c > max) max = c;
            else if (c < min) min = c;
        }

        // Build a histogram of non-zero counts.
        int[] hist = new int[max - min + 1];
        for (int i = 0; i < numHits; i++) {
            short c = counts[hits[i]];
            hist[c - min] += 1;
        }

        // Find kth largest by iterating from end of the histogram.
        int numGreaterEqual = 0;
        short kthGreatest = max;
        while (kthGreatest >= min) {
            numGreaterEqual += hist[kthGreatest - min];
            if (numGreaterEqual > k) break;
            else kthGreatest--;
        }
        int numGreater = numGreaterEqual - hist[kthGreatest - min];

        return new KthGreatest(max, kthGreatest, numGreater);
    }

    public boolean isEmpty() {
        return numHits == 0;
    }


}
