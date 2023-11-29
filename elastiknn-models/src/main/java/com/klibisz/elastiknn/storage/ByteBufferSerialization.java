package com.klibisz.elastiknn.storage;

import scala.util.control.Exception;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteBufferSerialization {
    public static final int numBytesInInt = 4;
    public static final int numBytesInFloat = 4;

    public static final ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;

    public static byte[] writeInt(final int i) {
        ByteBuffer bb;
        final int a = Math.abs(i);
        if (a <= Byte.MAX_VALUE) {
            bb = ByteBuffer.allocate(1).order(byteOrder);
            bb.asIntBuffer().put(i);
            return bb.array();
        } else if (a <= Short.MAX_VALUE) {
            bb = ByteBuffer.allocate(2).order(byteOrder);
            bb.asIntBuffer().put(i);
            return bb.array();
        } else {
            bb = ByteBuffer.allocate(4).order(byteOrder);
            bb.asIntBuffer().put(i);
            return bb.array();
        }
    }

    public static byte[] writeFloats(final float[] farr) {
        ByteBuffer bb = ByteBuffer.allocate(farr.length * numBytesInFloat).order(byteOrder);
        bb.asFloatBuffer().put(farr);
        return bb.array();
    }

    public static float[] readFloats(final byte[] barr, int offset, int length) {
        float[] dst = new float[length / numBytesInFloat];
        ByteBuffer bb = ByteBuffer.wrap(barr, offset, length).order(byteOrder);
        bb.asFloatBuffer().get(dst);
        return dst;
    }

}
