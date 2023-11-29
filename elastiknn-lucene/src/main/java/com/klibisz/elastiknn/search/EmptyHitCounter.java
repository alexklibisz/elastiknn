package com.klibisz.elastiknn.search;

import org.apache.lucene.search.KthGreatest;

public final class EmptyHitCounter implements HitCounter {

    @Override
    public void increment(int key) {}

    @Override
    public void increment(int key, short count) {}

    @Override
    public boolean isEmpty() {
        return true;
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
        return new KthGreatest.Result((short) 0, 0, 0);
    }
}
