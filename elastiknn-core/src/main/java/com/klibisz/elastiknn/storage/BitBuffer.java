package com.klibisz.elastiknn.storage;

public interface BitBuffer {
    void putOne();
    void putZero();
    byte[] toByteArray();

    class IntBuffer implements BitBuffer {

        private final byte[] prefix;
        private int i = 0;
        private int b = 0;

        public IntBuffer(byte[] prefix) {
            this.prefix = prefix;
        }

        public IntBuffer() {
            this.prefix = new byte[0];
        }

        @Override
        public void putOne() {
            this.b += (1 << this.i);
            this.i += 1;
        }

        @Override
        public void putZero() {
            this.i += 1;
        }

        @Override
        public byte[] toByteArray() {
            byte[] barr = UnsafeSerialization.writeInt(b);
            byte[] res = new byte[prefix.length + barr.length];
            System.arraycopy(prefix, 0, res, 0, prefix.length);
            System.arraycopy(barr, 0, res, prefix.length, barr.length);
            return res;
        }
    }

}