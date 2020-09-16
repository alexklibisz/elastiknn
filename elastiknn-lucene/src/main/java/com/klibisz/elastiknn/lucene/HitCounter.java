package com.klibisz.elastiknn.lucene;

public interface HitCounter {

    void increment(int key, short count);

    boolean isEmpty();

    short get(int key);

    int numHits();

    class KthGreatest {
        public final short kthGreatest;
        public final int numGreaterThan;
        public KthGreatest(short kthGreatest, int numGreaterThan) {
            this.kthGreatest = kthGreatest;
            this.numGreaterThan = numGreaterThan;
        }
    }

    KthGreatest kthGreatest(int k);

    interface Iterator {
        void advance();
        boolean hasNext();
        int docID();
        int count();
    }

    Iterator iterator();
}
