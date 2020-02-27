package com.klibisz.elastiknn.utils;

/**
 * Java implementations of some particularly performance-critical code paths.
 */
public class ArrayUtils {

    private static void unsortedException(int lit, int big) {
        throw new IllegalArgumentException(String.format("Called on unsorted array: %d came after %d", lit, big));
    }

    public static int sortedIntersectionCount(int [] xs, int [] ys) {
        int n = 0;
        int xi = 0;
        int yi = 0;
        int xmax = Integer.MIN_VALUE;
        int ymax = Integer.MIN_VALUE;
        while (xi < xs.length && yi < ys.length) {
            int x = xs[xi];
            int y = ys[yi];
            if (x < xmax) unsortedException(x, xmax);
            else xmax = x;
            if (y < ymax) unsortedException(y, ymax);
            else ymax = y;
            if (x < y) xi += 1;
            else if (x > y) yi += 1;
            else {
                n += 1;
                xi += 1;
                yi += 1;
            }
        }
        while(xi < xs.length) {
            if (xs[xi] < xmax) unsortedException(xs[xi], xmax);
            xi += 1;
        }
        while(yi < ys.length) {
            if (ys[yi] < ymax) unsortedException(ys[yi], ymax);
            yi += 1;
        }
        return n;
    }

//    implementation 'commons-codec:commons-codec:1.14'
//    import org.apache.commons.codec.digest.MurmurHash3;
//    import java.nio.ByteBuffer;
//    import java.nio.IntBuffer;
//    import java.nio.LongBuffer;
//    public static int orderedMurmurHash(long[] xs) {
//        ByteBuffer byteBuffer = ByteBuffer.allocate(xs.length * 8);
//        LongBuffer longBuffer = byteBuffer.asLongBuffer();
//        longBuffer.put(xs);
//        return MurmurHash3.hash32x86(byteBuffer.array(), 0, xs.length, 0xb592f7ae);
//    }

}
