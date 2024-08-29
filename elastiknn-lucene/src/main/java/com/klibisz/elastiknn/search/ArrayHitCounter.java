package com.klibisz.elastiknn.search;

import jdk.internal.vm.annotation.ForceInline;
import org.apache.lucene.search.DocIdSetIterator;

import java.util.Arrays;

public final class ArrayHitCounter implements HitCounter {

    // Mapping an integer doc ID to the number of times it has occurred.
    // E.g., if document 10 has been matched 11 times, then docIdToCount[10] = 11.
    private final short[] docIdToCount;

    // Mapping an integer count to the number of times it has occurred.
    // E.g., if there are 10 docs which have each been matched 11 times, countToCount[11] = 10.
    private short[] countToCount;

    private int minDocId;
    private int maxDocId;

    private int maxCount = 0;

    public ArrayHitCounter(int numDocs, int expectedMaxCount) {
        docIdToCount = new short[numDocs];
        countToCount = new short[expectedMaxCount + 1];
        minDocId = Integer.MAX_VALUE;
        maxDocId = 0;
    }

    public ArrayHitCounter(int numDocs) {
        this(numDocs, 10);
    }

    @ForceInline
    private void incrementKeyByCount(int docId, short count) {
        int newCount = (docIdToCount[docId] += count);
        if (newCount > maxCount) maxCount = newCount;

        // Potentially grow the count arrays.
        if (newCount >= countToCount.length) {
            countToCount = Arrays.copyOf(countToCount, newCount + 1);
        }

        // Update the old count.
        int oldCount = newCount - count;
        if (oldCount > 0) countToCount[oldCount] -= 1;

        // Update the new count.
        countToCount[newCount]++;

        // Update min/max doc IDs.
        if (docId < minDocId) minDocId = docId;
        if (docId > maxDocId) maxDocId = docId;
    }

    @Override
    public void increment(int key) {
        incrementKeyByCount(key, (short) 1);
    }

    @Override
    public void increment(int key, short count) {
        incrementKeyByCount(key, count);
    }

    @Override
    public short get(int key) {
        return docIdToCount[key];
    }

    @Override
    public int capacity() {
        return docIdToCount.length;
    }

    @Override
    public DocIdSetIterator docIdSetIterator(int candidates) {
        if (maxCount == 0) return DocIdSetIterator.empty();
        else {

            // Loop backwards through countToCount to figure out a few things needed for the DocIdSetIterator:
            // * the minimum count that's required for a document to be a candidate.
            // * the minimum doc ID that we should start iterating at.
            // * the maximum doc ID that we should start iterating at.
            int kthGreatest = maxCount;
            int numGreaterEqual = 0;
            while (true) {
                numGreaterEqual += countToCount[kthGreatest];
                if (kthGreatest > 1 && numGreaterEqual < candidates) kthGreatest--;
                else break;
            }
            // Java seems to want me to do this in order to reuse the values in the class below.
            final int finalKthGreatest = kthGreatest;
            final int finalMinDocId = minDocId;
            final int finalMaxDocId = maxDocId;
            final int numGreaterThan = numGreaterEqual - countToCount[kthGreatest];

            // Return an iterator over the doc ids >= the min candidate count.
            return new DocIdSetIterator() {

                // Important that this starts at -1. Need a boolean to denote that it has started iterating.
                private int docID = -1;
                private boolean started = false;

                // Track the number of total IDs emitted.
                private int numTotalEmitted = 0;

                // The threshold of IDs w/ count = kthGreatest that can be emitted.
                private final int numEqThreshold = candidates - numGreaterThan;

                // Track the number of IDs w/ count = kthGreatest that have been emitted
                private int numEqEmitted = 0;


                @Override
                public int docID() {
                    return docID;
                }

                @Override
                public int nextDoc() {

                    if (!started) {
                        started = true;
                        docID = finalMinDocId - 1;
                    }

                    // Ensure that docs with count = kgr.kthGreatest are only emitted when there are fewer
                    // than `candidates` docs with count > kgr.kthGreatest.
                    while (true) {
                        if (numTotalEmitted == candidates || docID + 1 > finalMaxDocId) {
                            docID = DocIdSetIterator.NO_MORE_DOCS;
                            return docID;
                        } else {
                            docID++;
                            if (docIdToCount[docID] > finalKthGreatest) {
                                numTotalEmitted++;
                                return docID;
                            } else if (docIdToCount[docID] == finalKthGreatest && numEqEmitted < numEqThreshold) {
                                numEqEmitted++;
                                numTotalEmitted++;
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
                    return finalMaxDocId - finalMinDocId;
                }
            };
        }
    }
}