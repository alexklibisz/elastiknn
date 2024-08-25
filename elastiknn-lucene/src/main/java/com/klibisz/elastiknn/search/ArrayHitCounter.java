package com.klibisz.elastiknn.search;

import org.apache.lucene.search.DocIdSetIterator;

import java.util.Arrays;


public final class ArrayHitCounter implements HitCounter {

    private final short[] docIdToCount;
    private final short[] countToFrequency;
    private final int[] countToMinDocId;
    private final int[] countToMaxDocId;
    private int maxCount;


    public ArrayHitCounter(int capacity, int maxExpectedCount) {
        this.docIdToCount = new short[capacity];
        this.countToFrequency = new short[maxExpectedCount + 1];
        this.countToMinDocId = new int[maxExpectedCount + 1];
        Arrays.fill(countToMinDocId, Integer.MAX_VALUE);
        this.countToMaxDocId = new int[maxExpectedCount + 1];
        this.maxCount = 0;
    }

    private void incrementKeyByCount(int docId, short count) {
        int newCount = (docIdToCount[docId] += count);
        if (newCount > maxCount) maxCount = newCount;

        // Updates for the old count.
        int oldCount = newCount - count;
        if (oldCount > 0) {
            countToFrequency[oldCount] -= 1;
            if (docId < countToMinDocId[oldCount]) countToMinDocId[oldCount] = docId;
            if (docId > countToMaxDocId[oldCount]) countToMaxDocId[oldCount] = docId;
        }

        // Updates for the new count.
        countToFrequency[newCount] += 1;
        if (docId < countToMinDocId[newCount]) countToMinDocId[newCount] = docId;
        if (docId > countToMaxDocId[newCount]) countToMaxDocId[newCount] = docId;
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
            // Loop backwards through the countToFrequency array to figure out a few things needed for the iterator:
            // 1. the minimum count that's required for a document to be a candidate
            // 2. the minimum doc ID that we should start iterating at
            // 3. and the maximum doc ID that we should iterate to
            int minCount = maxCount;
            int minDocId = Integer.MAX_VALUE;
            int maxDocId = Integer.MIN_VALUE;
            int accumulated = 0;
            while (accumulated < candidates && minCount > 0) {
                int count = countToFrequency[minCount];
                if (count > 0) {
                    accumulated += count;
                    int maybeNewMinDocId = countToMinDocId[minCount];
                    if (maybeNewMinDocId < minDocId) minDocId = maybeNewMinDocId;
                    int maybeNewMaxDocId = countToMaxDocId[minCount];
                    if (maybeNewMaxDocId > maxDocId) maxDocId = maybeNewMaxDocId;
                }
                minCount -= 1;
            }
//            minCount = Math.max(1, minCount);
            int numGreaterThanMinCount = accumulated - countToFrequency[minCount];
            return new _DocIdSetIterator(candidates, minDocId, maxDocId, minCount, numGreaterThanMinCount, docIdToCount);
        }
    }

    private static final class _DocIdSetIterator extends DocIdSetIterator {

        private final int candidates;
        private final int minDocId;
        private final int maxDocId;
        private final int minCount;
        private final int numGreaterThanMinCount;
        private final short[] docIdToCount;
        private int currentDocId;
        private int numEmitted;
        private int numEmittedEqualToMinCount;

        public _DocIdSetIterator(int candidates, int minDocId, int maxDocId, int minCount, int numGreaterThanMinCount, short[] docIdToCount) {
            this.candidates = candidates;
            this.minDocId = minDocId;
            this.maxDocId = maxDocId;
            this.minCount = minCount;
            this.numGreaterThanMinCount = numGreaterThanMinCount;
            this.docIdToCount = docIdToCount;
            this.currentDocId = minDocId - 1;
            this.numEmitted = 0;
            this.numEmittedEqualToMinCount = 0;
        }

        @Override
        public int docID() {
            return currentDocId;
        }

        @Override
        public int nextDoc() {
            while (true) {
                if (numEmitted == candidates || currentDocId + 1 > maxDocId) {
                    currentDocId = DocIdSetIterator.NO_MORE_DOCS;
                    return currentDocId;
                } else {
                    currentDocId++;
                    int count = docIdToCount[currentDocId];
                    if (count > minCount) {
                        numEmitted++;
                        return currentDocId;
                    } else if (count == minCount && numEmittedEqualToMinCount < candidates - numGreaterThanMinCount) {
                        numEmitted++;
                        numEmittedEqualToMinCount++;
                        return currentDocId;
                    }
                }
            }
        }

        @Override
        public int advance(int target) {
            while (currentDocId < target) nextDoc();
            return currentDocId;
        }

        @Override
        public long cost() {
            return maxDocId - minDocId;
        }
    }
}
