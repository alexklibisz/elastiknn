package com.klibisz.elastiknn.serialization;

import java.io.*;

public class ByteArrayCodec {

    public static byte[] writeInts(int[] arr) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        for (int value : arr) dout.writeInt(value);
        byte[] barr = bout.toByteArray();
        bout.close();
        dout.close();
        return barr;
    }

    public static int[] readInts(byte[] arr, int skip, int length) throws IOException {
        ByteArrayInputStream bin = new ByteArrayInputStream(arr);
        DataInputStream din = new DataInputStream(bin);
        int[] iarr = new int[length];
        for (int i = 0; i < iarr.length; i++) iarr[i] = din.readInt();
        bin.close();
        din.close();
        return iarr;
    }

}
