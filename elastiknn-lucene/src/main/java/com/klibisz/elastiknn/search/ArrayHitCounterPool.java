package com.klibisz.elastiknn.search;

/**
 * A pool of ArrayHitCounters which can be checked out
 * and reused as ThreadLocal variables.
 */
public class ArrayHitCounterPool {

    // A private intermediate class that gets stored within the ThreadLocal
    // and allows us to reuse or rebuild a new ArrayHitCounter.
    private static class Builder {
        private ArrayHitCounter ahc;

        public ArrayHitCounter getArrayHitCounter(int capacity) {
            // If it's the first call or the desired capacity exceeds the existing counter, we build a new one.
            if (ahc == null || capacity > ahc.capacity()) ahc = new ArrayHitCounter(capacity);
            // If the desired capacity is less or equal to existing, we reuse.
            else ahc.reset();
            return ahc;
        }
    }

    private static ThreadLocal<Builder> builders = ThreadLocal.withInitial(() -> new Builder());

    public static ArrayHitCounter getThreadLocal(int capacity) {
        return builders.get().getArrayHitCounter(capacity);
    }
}
