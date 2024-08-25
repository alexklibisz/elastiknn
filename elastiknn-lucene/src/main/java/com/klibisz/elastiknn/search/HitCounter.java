package com.klibisz.elastiknn.search;

import org.apache.lucene.search.DocIdSetIterator;

/**
 * Abstraction for counting hits for a particular query.
 */
public interface HitCounter {

    void increment(int key);

    void increment(int key, short count);


    short get(int key);

    int capacity();

    DocIdSetIterator docIdSetIterator(int k);

}
