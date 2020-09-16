package com.klibisz.elastiknn.lucene;

import it.unimi.dsi.fastutil.ints.Int2ShortMap;
import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

public class FastutilHitCounter implements HitCounter {

    private Int2ShortOpenHashMap m;

    public FastutilHitCounter(int expected) {
        m = new Int2ShortOpenHashMap(expected);
    }

    @Override
    public void increment(int key, short count) {
        m.addTo(key, count);
    }

    @Override
    public boolean isEmpty() {
        return m.isEmpty();
    }

    @Override
    public short get(int key) {
        return m.get(key);
    }

    @Override
    public int numHits() {
        return m.size();
    }

    @Override
    public KthGreatest kthGreatest(int k) {

        short[] counts = m.values().toShortArray();

        // Find the min and max values.
        short max = counts[0];
        short min = counts[0];
        for (short c: counts) {
            if (c > max) max = c;
            else if (c < min) min = c;
        }

        // Build and populate a histogram for non-zero values.
        int[] hist = new int[max - min + 1];
        for (short c: counts) {
            hist[c - min] += 1;
        }

        // Find the kth largest value by iterating from the end of the histogram.
        int numGreaterEqual = 0;
        short kthGreatest = max;
        while (kthGreatest >= min) {
            numGreaterEqual += hist[kthGreatest - min];;
            if (numGreaterEqual > k) break;
            else kthGreatest--;
        }
        int numGreater = numGreaterEqual - hist[kthGreatest - min];

        return new KthGreatest(kthGreatest, numGreater);
    }

    @Override
    public Iterator iterator() {

        return new Iterator() {

            private final ObjectIterator<Int2ShortMap.Entry> iterator = m.int2ShortEntrySet().fastIterator();
            private Int2ShortMap.Entry entry;

            @Override
            public void advance() {
                entry = iterator.next();
            }

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public int docID() {
                return entry.getIntKey();
            }

            @Override
            public int count() {
                return entry.getShortValue();
            }
        };
    }
}
