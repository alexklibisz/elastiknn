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

    /**
     * Writes ints to a byte array. First int is the length of the array
     * @param iarr ints to serialize.
     * @return Array of bytes with length (4 + iarr.length).
     */
    public static byte[] writeInts(int[] iarr) {
        int bytesLen = iarr.length * numBytesInInt;
        byte[] buf = new byte[bytesLen + numBytesInInt];
        u.unsafe.putInt(buf, u.byteArrayOffset, iarr.length);
        u.unsafe.copyMemory(iarr, u.intArrayOffset, buf, u.byteArrayOffset + numBytesInInt, bytesLen);
        return buf;
    }

    public static int[] readInts(byte[] barr) {
        int intsLen = u.unsafe.getInt(barr, u.byteArrayOffset);
        int bytesLen = intsLen * numBytesInInt;
        int[] iarr = new int[intsLen];
        u.unsafe.copyMemory(barr, u.byteArrayOffset + numBytesInInt, iarr, u.intArrayOffset, bytesLen);
        return iarr;
    }

    public static byte[] writeFloats(float[] farr) {
        int bytesLen = farr.length * numBytesInFloat;
        byte[] buf = new byte[bytesLen + numBytesInInt];
        u.unsafe.putInt(buf, u.byteArrayOffset, farr.length);
        u.unsafe.copyMemory(farr, u.floatArrayOffset, buf, u.byteArrayOffset + numBytesInInt, bytesLen);
        return buf;
    }

    public static float[] readFloats(byte[] barr) {
        int floatsLen = u.unsafe.getInt(barr, u.floatArrayOffset);
        int bytesLen = floatsLen * numBytesInFloat;
        float[] farr = new float[floatsLen];
        u.unsafe.copyMemory(barr, u.byteArrayOffset + numBytesInInt, farr, u.floatArrayOffset, bytesLen);
        return farr;
    }

    private static class UnsafeUtil {
        public final Unsafe unsafe;
        public final long intArrayOffset;
        public final long floatArrayOffset;
        public final long byteArrayOffset;
        public UnsafeUtil() throws NoSuchFieldException, IllegalAccessException {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
            intArrayOffset = unsafe.arrayBaseOffset(int[].class);
            floatArrayOffset = unsafe.arrayBaseOffset(float[].class);
            byteArrayOffset = unsafe.arrayBaseOffset(byte[].class);
        }
    }

}