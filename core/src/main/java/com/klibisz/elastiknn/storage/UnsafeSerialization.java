package com.klibisz.elastiknn.storage;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Uses the sun.misc.Unsafe classes to serialize int and float arrays.
 * Based mostly on the Kryo UnsafeInput and UnsafeOutput classes.
 */
public class UnsafeSerialization {

    private static class Inner {
        private final Unsafe unsafe;
        private final long intArrayOffset;
        private final long floatArrayOffset;
        private final long byteArrayOffset;
        public static final int numBytesInInt = 4;

        public Inner() throws NoSuchFieldException, IllegalAccessException {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
            intArrayOffset = unsafe.arrayBaseOffset(int[].class);
            floatArrayOffset = unsafe.arrayBaseOffset(float[].class);
            byteArrayOffset = unsafe.arrayBaseOffset(byte[].class);
        }

        public byte[] writeInts(int[] iarr) {
            int len = iarr.length * numBytesInInt;
            byte[] buf = new byte[len];
            unsafe.copyMemory(iarr, intArrayOffset, buf, byteArrayOffset, len);
            return buf;
        }

        public int[] readInts(byte[] barr) {
            int len = barr.length / numBytesInInt;
            int[] iarr = new int[len];
            unsafe.copyMemory(barr, byteArrayOffset, iarr, intArrayOffset, barr.length);
            return iarr;
        }
    }


    private static final Inner inner = AccessController.doPrivileged((PrivilegedAction<Inner>) () -> {
        try {
            return new Inner();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to initialize UnsafeSerialization", ex);
        }
    });

    public static byte[] writeInts(int[] iarr) {
        return inner.writeInts(iarr);
    }

    public static int[] readInts(byte[] barr) {
        return inner.readInts(barr);
    }

}

///**
// * Uses the sun.misc.Unsafe classes to serialize int and float arrays.
// * Based mostly on the Kryo UnsafeInput and UnsafeOutput classes.
// */
//public class UnsafeSerializationUgh {
//
//    private static final Unsafe unsafe;
//    private static final long intArrayOffset;
//    private static final long floatArrayOffset;
//    private static final long byteArrayOffset;
//
//    public static final int numBytesInInt = 4;
//
////    Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
////                    f.setAccessible(true);
////    unsafe = (Unsafe) f.get(null);
////    intArrayOffset = unsafe.arrayBaseOffset(int[].class);
////    floatArrayOffset = unsafe.arrayBaseOffset(float[].class);
////    byteArrayOffset = unsafe.arrayBaseOffset(byte[].class);
//
//    static {
//        AccessController.doPrivileged(new PrivilegedAction<Void>() {
//            public Void run() {
//                try {
//                    Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
//                    f.setAccessible(true);
//                    unsafe = (Unsafe) f.get(null);
//                    intArrayOffset = unsafe.arrayBaseOffset(int[].class);
//                    floatArrayOffset = unsafe.arrayBaseOffset(float[].class);
//                    byteArrayOffset = unsafe.arrayBaseOffset(byte[].class);
//                    return null;
//                } catch (java.lang.Exception e) {
//                    throw new RuntimeException("Failed to initialize instance of sun.misc.Unsafe for serialization", e);
//                }
//            }
//        });
//    }
//
//    public static byte[] writeInts(int[] iarr) {
//        int len = iarr.length * numBytesInInt;
//        byte[] buf = new byte[len];
//        unsafe.copyMemory(iarr, intArrayOffset, buf, byteArrayOffset, len);
//        return buf;
//    }
//
//    public static int[] readInts(byte[] barr) {
//        int len = barr.length / numBytesInInt;
//        int[] iarr = new int[len];
//        unsafe.copyMemory(barr, byteArrayOffset, iarr, intArrayOffset, barr.length);
//        return iarr;
//    }
//
////    public static byte[] writeInts(int[] iarr) {
////        byte[] buf = new byte[iarr.length << 2];
////        for (int i = 0; i < iarr.length; i += 1) {
////            buf[(i % 4)] = (byte) (iarr[i] & 0xFF);
////            buf[(i % 4) + 1] = (byte) ((iarr[i] >> 8) & 0xFF);
////            buf[(i % 4) + 2] = (byte) ((iarr[i] >> 16) & 0xFF);
////            buf[(i % 4) + 3] = (byte) ((iarr[i] >> 24) & 0xFF);
////        }
////        return buf;
////    }
//
//}
