package com.klibisz.elastiknn.search;

/**
 * Abstraction for counting hits for a particular query.
 */
public interface HitCounter {

    void increment(int key);

    void increment(int key, short count);

    boolean isEmpty();

    short get(int key);

    int numHits();

    int capacity();

    int minKey();

    int maxKey();

    KthGreatestResult kthGreatest(int k);

}
