package com.klibisz.elastiknn.search;

import org.apache.lucene.search.KthGreatest;

/**
 * Abstraction for counting hits for a particular query.
 */
public interface HitCounter {

    void increment(int key);

    boolean isEmpty();

    short get(int key);

    int numHits();

    int capacity();

    int minKey();

    int maxKey();

    KthGreatest.Result kthGreatest(int k);

}
