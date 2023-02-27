package com.klibisz.elastiknn.search;

import org.apache.lucene.search.KthGreatest;

import java.util.HashMap;

public class HashMapHitCounter implements HitCounter {

    private final HashMap<Integer, Integer> counts;

    public HashMapHitCounter() {
        this.counts = new HashMap<Integer, Integer>();
    }

    @Override
    public void increment(int key, short count) {

    }

    @Override
    public void increment(int key, int count) {

    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public short get(int key) {
        return 0;
    }

    @Override
    public int numHits() {
        return 0;
    }

    @Override
    public int capacity() {
        return 0;
    }

    @Override
    public int minKey() {
        return 0;
    }

    @Override
    public int maxKey() {
        return 0;
    }

    @Override
    public KthGreatest.Result kthGreatest(int k) {
        return null;
    }
}
