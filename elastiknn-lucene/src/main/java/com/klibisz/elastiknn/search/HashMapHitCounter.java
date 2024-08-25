package com.klibisz.elastiknn.search;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.internal.hppc.IntIntHashMap;


public final class HashMapHitCounter implements HitCounter {

    private final IntIntHashMap docIdToCount;
    private final int[] countToFrequency;
    private final int[] countToMinDocId;
    private final int[] countToMaxDocId;
    private int minKey;
    private int maxKey;
    private int maxCount;


    public HashMapHitCounter(int expectedDocIdCount, int maxExpectedCount) {
        this.docIdToCount = new IntIntHashMap(expectedDocIdCount);
        this.countToFrequency = new int[maxExpectedCount + 1];
        this.countToMinDocId = new int[maxExpectedCount + 1];
        this.countToMaxDocId = new int[maxExpectedCount + 1];
        this.minKey = Integer.MAX_VALUE;
        this.maxKey = Integer.MIN_VALUE;
        this.maxCount = 0;
    }

    private void incrementKeyByCount(int key, int count) {
        int newCount = docIdToCount.putOrAdd(key, count, count);
        if (newCount > maxCount) maxCount = newCount;

        // Updates for the old count.
        int oldCount = newCount - count;
        if (oldCount > 0) {
            countToFrequency[oldCount] -= 1;
            if (key < countToMinDocId[oldCount] || countToMinDocId[oldCount] == 0) countToMinDocId[oldCount] = key;
            else if (key > countToMaxDocId[oldCount]) countToMaxDocId[oldCount] = key;
        }

        // Updates for the new count.
        countToFrequency[newCount] += 1;
        if (key < countToMinDocId[newCount] || countToMinDocId[newCount] == 0) countToMinDocId[newCount] = key;
        else if (key > countToMinDocId[newCount]) countToMinDocId[newCount] = key;
    }

    @Override
    public void increment(int key) {
        incrementKeyByCount(key, 1);
    }

    @Override
    public void increment(int key, short count) {
        incrementKeyByCount(key, count);
    }

    @Override
    public boolean isEmpty() {
        return docIdToCount.isEmpty();
    }

    @Override
    public short get(int key) {
        return (short) docIdToCount.get(key);
    }

    @Override
    public int numHits() {
        return docIdToCount.size();
    }

    @Override
    public int capacity() {
        return docIdToCount.size();
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
        return null;
    }

    @Override
    public DocIdSetIterator docIdSetIterator(int candidates) {
        if (isEmpty()) return DocIdSetIterator.empty();
        else {
            // Look backwards through the countToFrequency array to figure out a few things needed for the iterator:
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
                    if (countToMinDocId[minCount] < minDocId) minDocId = countToMinDocId[minCount];
                    if (countToMaxDocId[minCount] > maxDocId) maxDocId = countToMaxDocId[minCount];
                }
                minCount -= 1;
            }
            return new HashMapIntCounterDocIdSetIterator(minDocId, maxDocId, minCount, docIdToCount);
        }
    }

    private static final class HashMapIntCounterDocIdSetIterator extends DocIdSetIterator {

        private final int minDocId;
        private final int maxDocId;

        private final int minCount;

        private final IntIntHashMap docIdToCount;

        private int currentDocId;

        public HashMapIntCounterDocIdSetIterator(int minDocId, int maxDocId, int minCount, IntIntHashMap docIdToCount) {
            this.minDocId = minDocId;
            this.maxDocId = maxDocId;
            this.minCount = Math.max(1, minCount);
            this.docIdToCount = docIdToCount;
            this.currentDocId = minDocId - 1;
        }

        @Override
        public int docID() {
            return currentDocId;
        }

        @Override
        public int nextDoc() {
            while (true) {
                currentDocId++;
                if (docIdToCount.getOrDefault(currentDocId, 0) >= minCount) {
                    return currentDocId;
                } else if (currentDocId > maxDocId) {
                    currentDocId = DocIdSetIterator.NO_MORE_DOCS;
                    return currentDocId;
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
