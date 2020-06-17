package com.klibisz.elastiknn.storage;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Uses the sun.misc.Unsafe classes to serialize int and float arrays for optimal performance based on benchmarking.
 * This is largely a simplification of the UnsafeInput and UnsafeOutput classes from the Kryo library.
 */
public class UnsafeSerialization {

    public static final int numBytesInInt = 4;
    public static final int numBytesInFloat = 4;

    private static final UnsafeUtil u = AccessController.doPrivileged((PrivilegedAction<UnsafeUtil>) () -> {
        try {
            return new UnsafeUtil();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to initialize UnsafeSerialization", ex);
        }
    });

    public static byte[] writeInt(final int i) {
        final int a = Math.abs(i);
        if (a <= Byte.MAX_VALUE) {
            final byte[] buf = new byte[1];
            u.unsafe.putInt(buf, u.byteArrayOffset, i);
            return buf;
        } else if (a <= Short.MAX_VALUE) {
            final byte[] buf = new byte[2];
            u.unsafe.putInt(buf, u.byteArrayOffset, i);
            return buf;
        } else {
            final byte[] buf = new byte[4];
            u.unsafe.putInt(buf, u.byteArrayOffset, i);
            return buf;
        }
    }

    public static int readInt(final byte[] barr) {
        return u.unsafe.getInt(barr, u.byteArrayOffset);
    }

    /**
     * Writes ints to a byte array.
     * @param iarr ints to serialize.
     * @return Array of bytes with length (4 * iarr.length).
     */
    public static byte[] writeInts(final int[] iarr) {
        final int bytesLen = iarr.length * numBytesInInt;
        byte[] buf = new byte[bytesLen];
        u.unsafe.copyMemory(iarr, u.intArrayOffset, buf, u.byteArrayOffset, bytesLen);
        return buf;
    }

    /**
     * Reads ints from a byte array.
     */
    public static int[] readInts(final byte[] barr, final int offset, final int length) {
        final int[] iarr = new int[length / numBytesInInt];
        u.unsafe.copyMemory(barr, offset + u.byteArrayOffset, iarr, u.intArrayOffset, length);
        return iarr;
    }

    /**
     * Writes floats to a byte array.
     */
    public static byte[] writeFloats(final float[] farr) {
        final int bytesLen = farr.length * numBytesInFloat;
        final byte[] buf = new byte[bytesLen];
        u.unsafe.copyMemory(farr, u.floatArrayOffset, buf, u.byteArrayOffset, bytesLen);
        return buf;
    }

    /**
     * Reads floats from a byte array.
     */
    public static float[] readFloats(final byte[] barr, final int offset, final int length) {
        final float[] farr = new float[length / numBytesInFloat];
        u.unsafe.copyMemory(barr, offset + u.byteArrayOffset, farr, u.floatArrayOffset, length);
        return farr;
    }

    private static class UnsafeUtil {
        public final Unsafe unsafe;
        public final long intArrayOffset;
        public final long floatArrayOffset;
        public final long byteArrayOffset;
        public UnsafeUtil() throws NoSuchFieldException, IllegalAccessException {
            final Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
            intArrayOffset = unsafe.arrayBaseOffset(int[].class);
            floatArrayOffset = unsafe.arrayBaseOffset(float[].class);
            byteArrayOffset = unsafe.arrayBaseOffset(byte[].class);
        }
    }

}
