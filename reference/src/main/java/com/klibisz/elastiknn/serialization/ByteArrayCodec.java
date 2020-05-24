package com.klibisz.elastiknn.serialization;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ByteArrayCodec {

    public static byte[] writeInts(int[] arr) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        for (int value : arr) dout.writeInt(value);
        byte[] barr = bout.toByteArray();
        dout.close();
        return barr;
    }

}
