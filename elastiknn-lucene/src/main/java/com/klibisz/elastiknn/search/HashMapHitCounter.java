package com.klibisz.elastiknn.search;

import org.apache.lucene.search.KthGreatest;
import org.apache.lucene.util.hppc.IntIntHashMap;

public final class HashMapHitCounter implements HitCounter {

    private final IntIntHashMap hashMap;
    private final int capacity;
    private int minKey;
    private int maxKey;

    public HashMapHitCounter(int capacity, int expectedElements, float loadFactor) {
        this.capacity = capacity;
        hashMap = new IntIntHashMap(expectedElements, loadFactor);
        minKey = capacity;
        maxKey = 0;
    }


    @Override
    public void increment(int key, short count) {
        hashMap.putOrAdd(key, count, count);
    }

    @Override
    public void increment(int key, int count) {
        minKey = Math.min(key, minKey);
        maxKey = Math.max(key, maxKey);
        hashMap.putOrAdd(key, count, count);
    }

    @Override
    public boolean isEmpty() {
        return hashMap.isEmpty();
    }

    @Override
    public short get(int key) {
        return (short) hashMap.get(key);
    }

    @Override
    public int numHits() {
        return hashMap.size();
    }

    @Override
    public int capacity() {
        return capacity;
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
    public KthGreatest.Result kthGreatest(int k) {
        return KthGreatest.kthGreatest(hashMap.values, Math.min(k, hashMap.size() - 1));
    }
}
