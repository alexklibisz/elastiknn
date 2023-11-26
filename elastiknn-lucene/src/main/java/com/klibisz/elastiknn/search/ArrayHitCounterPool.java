package com.klibisz.elastiknn.search;

public class ArrayHitCounterPool {

    private static class Container {
        private ArrayHitCounter ahc;

        public ArrayHitCounter get(int capacity) {
            if (ahc == null) ahc = new ArrayHitCounter(capacity);
            else if (capacity < ahc.capacity()) ahc.reset();
            else ahc = new ArrayHitCounter(capacity);
            return ahc;
        }
    }

    private static ThreadLocal<Container> containers = ThreadLocal.withInitial(() -> new Container());

    public static ArrayHitCounter getThreadLocal(int capacity) {
        return containers.get().get(capacity);
    }
}
