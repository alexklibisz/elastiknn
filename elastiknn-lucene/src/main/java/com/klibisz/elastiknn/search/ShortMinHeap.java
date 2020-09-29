package com.klibisz.elastiknn.search;

/**
 * Min heap where the values are shorts.
 * Credit to: https://www.geeksforgeeks.org/min-heap-in-java/
 */
public class ShortMinHeap {
    private short[] heap;
    private int size;
    private int maxsize;

    private static final int FRONT = 1;

    public ShortMinHeap(int maxsize)
    {
        this.maxsize = maxsize;
        this.size = 0;
        this.heap = new short[this.maxsize + 1];
        this.heap[0] = Short.MIN_VALUE;
    }

    // Return the position of the parent for the node currently at pos.
    private int parent(int pos) {
        return pos / 2;
    }

    // Return the position of the left child for the node currently at pos.
    private int leftChild(int pos) {
        return (2 * pos);
    }

    // Return the position of the right child for the node currently at pos.
    private int rightChild(int pos) {
        return (2 * pos) + 1;
    }

    // Return true if the passed node is a leaf node.
    private boolean isLeaf(int pos) {
        return pos >= (size / 2) && pos <= size;
    }

    // Swap two nodes of the heap.
    private void swap(int fpos, int spos) {
        short tmp;
        tmp = heap[fpos];
        heap[fpos] = heap[spos];
        heap[spos] = tmp;
    }

    // Heapify the node at pos.
    private void minHeapify(int pos) {

        // If the node is a non-leaf node and greater than any of its child.
        if (!isLeaf(pos)) {
            if (heap[pos] > heap[leftChild(pos)]
                    || heap[pos] > heap[rightChild(pos)]) {

                // Swap with the left child and heapify the left child.
                if (heap[leftChild(pos)] < heap[rightChild(pos)]) {
                    swap(pos, leftChild(pos));
                    minHeapify(leftChild(pos));
                }

                // Swap with the right child and heapify the right child.
                else {
                    swap(pos, rightChild(pos));
                    minHeapify(rightChild(pos));
                }
            }
        }
    }

    // Insert a node into the heap.
    public void insert(short element) {
        if (size >= maxsize) {
            return;
        }
        heap[++size] = element;
        int current = size;
        while (heap[current] < heap[parent(current)]) {
            swap(current, parent(current));
            current = parent(current);
        }
    }

    // Alias for inserting int that gets cast to a short.
    public void insert(int element) {
        insert((short) element);
    }

    // Build the min heap using the minHeapify.
    public void minHeapify() {
        for (int pos = (size / 2); pos >= 1; pos--) {
            minHeapify(pos);
        }
    }

    // Remove, return the minimum element from the heap.
    public short remove() {
        short popped = heap[FRONT];
        heap[FRONT] = heap[size--];
        minHeapify(FRONT);
        return popped;
    }

    // Driver code
    public static void main(String[] arg) {
        ShortMinHeap minHeap = new ShortMinHeap(3);
        minHeap.insert(5);
        minHeap.insert(3);
        minHeap.insert(17);
//        minHeap.minHeap();

        System.out.println(minHeap.remove());
        System.out.println(minHeap.remove());
        System.out.println(minHeap.remove());

    }

}

