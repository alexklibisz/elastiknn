package com.klibisz.elastiknn.search;

/**
 * Min heap where the values are shorts. Useful for tracking top counts for a query.
 * Based on the Python std. lib. implementation: https://docs.python.org/3.8/library/heapq.html#module-heapq
 */
public class ShortMinHeap {
    private short[] heap;
    private int size;
    private final int capacity;

    public ShortMinHeap(int capacity) {
        this.capacity = capacity;
        this.size = 0;
        this.heap = new short[capacity];
    }

    public int size() {
        return this.size;
    }

    public int capacity() { return this.capacity; }

    public short peek() {
        if (size == 0) {
            throw new IllegalStateException("Cannot peek an empty heap");
        } else {
            return heap[0];
        }
    }

    public void clear() {
        this.size = 0;
    }

    public void insert(short element) {
        if (size >= capacity) {
            throw new IllegalStateException("Cannot insert to full heap");
        } else {
            heap[size++] = element;
            siftDown(heap, 0, size - 1);
        }
    }

    public void insert(int element) {
        insert((short) element);
    }

    public short remove() {
        if (size > 1) {
            short min = heap[0];
            heap[0] = heap[--size];
            siftUp(heap, 0, size + 1);
            return min;
        } else if (size == 1) {
            return heap[--size];
        } else {
            throw new IllegalStateException("Cannot remove from empty heap");
        }
    }

    public short replace(short element) {
        short min = heap[0];
        heap[0] = element;
        siftUp(heap, 0, size);
        return min;
    }

    private static void siftDown(short[] heap, int startPos, int pos) {
        short newItem = heap[pos];
        while (pos > startPos) {
            int parentPos = (pos - 1) / 2;
            short parent = heap[parentPos];
            if (newItem < parent) {
                heap[pos] = parent;
                pos = parentPos;
                continue;
            }
            break;
        }
        heap[pos] = newItem;
    }

    private static void siftUp(short[] heap, int pos, int endPos) {
        int startPos = pos;
        short newItem = heap[pos];
        int childPos = 2 * pos + 1;
        while (childPos < endPos) {
            int rightPos = childPos + 1;
            if (rightPos < endPos && !(heap[childPos] < heap[rightPos])) {
                childPos = rightPos;
            }
            heap[pos] = heap[childPos];
            pos = childPos;
            childPos = 2 * pos + 1;
        }
        heap[pos] = newItem;
        siftDown(heap, startPos, pos);
    }



}

