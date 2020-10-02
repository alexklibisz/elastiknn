package com.klibisz.elastiknn.search;

/**
 * Abstraction for counting hits for a particular query.
 */
public interface HitCounter {

    short increment(int key, short count);

    short increment(int key, int count);

    boolean isEmpty();

    short get(int key);

    int capacity();

    int hits();

    int minKey();

    int maxKey();

}
