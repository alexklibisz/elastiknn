package com.klibisz.elastiknn.serialization;

import java.io.*;

public class BinaryCodecs {

    public static byte[] writeInts(int[] arr) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        dout.writeShort(arr.length);
        for (int value : arr) dout.writeShort(value);
        byte[] barr = bout.toByteArray();
        bout.close();
        dout.close();
        return barr;
    }

    public static int[] readInts(byte[] arr) throws IOException {
        ByteArrayInputStream bin = new ByteArrayInputStream(arr);
        DataInputStream din = new DataInputStream(bin);
        int length = din.readShort();
        int[] iarr = new int[length];
        for (int i = 0; i < length; i++) iarr[i] = din.readShort();
        bin.close();
        din.close();
        return iarr;
    }

}
