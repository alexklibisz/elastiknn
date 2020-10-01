package com.klibisz.elastiknn.search;

public class ConstantHitCounter implements HitCounter {

    private final int maxDocs;
    private final short constScore;
    private boolean isEmpty;

    public ConstantHitCounter(int maxDocs, short constScore) {
        this.maxDocs = maxDocs;
        this.constScore = constScore;
        this.isEmpty = true;
    }

    @Override
    public void increment(int key, short count) {
        isEmpty = false;
    }

    @Override
    public void increment(int key, int count) {
        increment(key, (short) count);
    }

    @Override
    public boolean isEmpty() {
        return isEmpty;
    }

    @Override
    public short get(int key) {
        return constScore;
    }

    @Override
    public int size() {
        return maxDocs;
    }

}
