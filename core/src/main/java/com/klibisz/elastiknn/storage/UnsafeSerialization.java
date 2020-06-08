package com.klibisz.elastiknn.storage;

import scala.Int;
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

    public static byte[] writeInt(int i) {
        int a = Math.abs(i);
        if (a <= Byte.MAX_VALUE) {
            byte[] buf = new byte[1];
            u.unsafe.putInt(buf, u.byteArrayOffset, i);
            return buf;
        } else if (a <= Short.MAX_VALUE) {
            byte[] buf = new byte[2];
            u.unsafe.putInt(buf, u.byteArrayOffset, i);
            return buf;
        } else {
            byte[] buf = new byte[4];
            u.unsafe.putInt(buf, u.byteArrayOffset, i);
            return buf;
        }
    }

    /**
     * Writes ints to a byte array. Encodes the length of the int array as the first 4 bytes of the byte array.
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

    /**
     * Reads ints from a byte array. Expects that the first 4 bytes in the array are the length of the int array.
     * @param barr
     * @return
     */
    public static int[] readInts(byte[] barr) {
        int intsLen = u.unsafe.getInt(barr, u.byteArrayOffset);
        int bytesLen = intsLen * numBytesInInt;
        int[] iarr = new int[intsLen];
        u.unsafe.copyMemory(barr, u.byteArrayOffset + numBytesInInt, iarr, u.intArrayOffset, bytesLen);
        return iarr;
    }

    /**
     * Writes floats to a byte array. Encodes the length of the float array as the first 4 bytes of the byte array.
     * @param farr
     * @return
     */
    public static byte[] writeFloats(float[] farr) {
        int bytesLen = farr.length * numBytesInFloat;
        byte[] buf = new byte[bytesLen + numBytesInInt];
        u.unsafe.putInt(buf, u.byteArrayOffset, farr.length);
        u.unsafe.copyMemory(farr, u.floatArrayOffset, buf, u.byteArrayOffset + numBytesInInt, bytesLen);
        return buf;
    }

    /**
     * Reads floats from a byte array. Expects that the first 4 bytes in the array are the length of the float array.
     * @param barr
     * @return
     */
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