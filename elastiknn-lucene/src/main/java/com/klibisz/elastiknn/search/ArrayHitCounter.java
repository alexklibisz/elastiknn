package com.klibisz.elastiknn.search;

import jdk.internal.vm.annotation.ForceInline;
import org.apache.lucene.search.DocIdSetIterator;

public final class ArrayHitCounter implements HitCounter {

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
            if (key < minKey) minKey = key;
            if (key > maxKey) maxKey = key;
        }
        if (after > maxValue) maxValue = after;
    }

    @Override
    public void increment(int key, short count) {
        short after = (counts[key] += count);
        if (after == count) {
            numHits++;
            if (key < minKey) minKey = key;
            if (key > maxKey) maxKey = key;
        }
        if (after > maxValue) maxValue = after;
    }

    @Override
    public short get(int key) {
        return counts[key];
    }

    @Override
    public int capacity() {
        return counts.length;
    }



    @ForceInline
    private KthGreatestResult kthGreatest(int k) {
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

        while (true) {
            numGreaterEqual += hist[kthGreatest];
            if (kthGreatest > 1 && numGreaterEqual < k) kthGreatest--;
            else break;
        }

        // Finally we find the number that were greater than the kth greatest count.
        // There's a special case if kthGreatest is zero, then the number that were greater is the number of hits.
        int numGreater = numGreaterEqual - hist[kthGreatest];
        return new KthGreatestResult(kthGreatest, numGreater);
    }

    @Override
    public DocIdSetIterator docIdSetIterator(int candidates) {
        if (numHits == 0) return DocIdSetIterator.empty();
        else {

            KthGreatestResult kgr = kthGreatest(candidates);

            // Return an iterator over the doc ids >= the min candidate count.
            return new DocIdSetIterator() {

                // Important that this starts at -1. Need a boolean to denote that it has started iterating.
                private int docID = -1;
                private boolean started = false;

                // Track the number of ids emitted, and the number of ids with count = kgr.kthGreatest emitted.
                private int numEmitted = 0;
                private int numEq = 0;

                @Override
                public int docID() {
                    return docID;
                }

                @Override
                public int nextDoc() {

                    if (!started) {
                        started = true;
                        docID = minKey - 1;
                    }

                    // Ensure that docs with count = kgr.kthGreatest are only emitted when there are fewer
                    // than `candidates` docs with count > kgr.kthGreatest.
                    while (true) {
                        if (numEmitted == candidates || docID + 1 > maxKey) {
                            docID = DocIdSetIterator.NO_MORE_DOCS;
                            return docID;
                        } else {
                            docID++;
                            if (counts[docID] > kgr.kthGreatest) {
                                numEmitted++;
                                return docID;
                            } else if (counts[docID] == kgr.kthGreatest && numEq < candidates - kgr.numGreaterThan) {
                                numEq++;
                                numEmitted++;
                                return docID;
                            }
                        }
                    }
                }

                @Override
                public int advance(int target) {
                    while (docID < target) nextDoc();
                    return docID();
                }

                @Override
                public long cost() {
                    return maxKey - minKey;
                }
            };
        }
    }

}