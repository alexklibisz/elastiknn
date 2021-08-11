package com.klibisz.elastiknn.storage;

import org.apache.lucene.codecs.bloom.MurmurHash2;

import java.nio.ByteBuffer;

public class BitBuffer {

    private final int prefix;
    private int i = 0;
    private int b = 0;

    public BitBuffer(int prefix) {
        this.prefix = prefix;
    }

    public BitBuffer() {
        this.prefix = 0;
    }

    public void putOne() {
        this.b += (1 << this.i);
        this.i += 1;
    }

    public void putZero() {
        this.i += 1;
    }

    public byte[] toByteArray() {
        ByteBuffer bbuf = ByteBuffer.allocate(8);
        bbuf.putInt(prefix);
        bbuf.putInt(b);
        return bbuf.array();
    }
}