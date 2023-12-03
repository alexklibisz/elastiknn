package com.klibisz.elastiknn.storage;

import scala.util.control.Exception;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteBufferSerialization {
    public static final int numBytesInInt = 4;
    public static final int numBytesInFloat = 4;

    public static final ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;

    public static byte[] writeInt(final int i) {
        final int a = Math.abs(i);
        if (a <= Byte.MAX_VALUE) {
            return new byte[]{(byte) i};
        } else if (a <= Short.MAX_VALUE) {
            return new byte[]{
                (byte) (i & 0xFF),
                (byte) ((i >> 8) & 0xFF)
            };
        } else {
            return new byte[]{
                (byte) (i & 0xFF),
                (byte) ((i >> 8) & 0xFF),
                (byte) ((i >> 16) & 0xFF),
                (byte) ((i >> 24) & 0xFF),
            };
        }
    }

    public static int readInt(final byte[] barr) {
        if (barr.length == 1) {
            return barr[0];
        } else if (barr.length == 2) {
            ByteBuffer bb = ByteBuffer.wrap(barr).order(byteOrder);
            return bb.getShort();
        } else {
            ByteBuffer bb = ByteBuffer.wrap(barr).order(byteOrder);
            return bb.getInt();
        }
    }

    public static byte[] writeInts(final int[] iarr) {
        ByteBuffer bb = ByteBuffer.allocate(iarr.length * numBytesInInt).order(byteOrder);
        bb.asIntBuffer().put(iarr);
        return bb.array();
    }

    public static byte[] writeIntsWithPrefix(int prefix, final int[] iarr) {
        ByteBuffer bb = ByteBuffer.allocate((iarr.length + 1) * numBytesInInt).order(byteOrder);
        bb.asIntBuffer().put(prefix).position(1).put(iarr);
        return bb.array();
    }

    public static int[] readInts(final byte[] barr, final int offset, final int length) {
        int[] dst = new int[length / numBytesInInt];
        ByteBuffer bb = ByteBuffer.wrap(barr, offset, length).order(byteOrder);
        bb.asIntBuffer().get(dst);
        return dst;
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
