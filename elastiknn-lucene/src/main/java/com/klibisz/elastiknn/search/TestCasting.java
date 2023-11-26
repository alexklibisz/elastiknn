package com.klibisz.elastiknn.search;

public class TestCasting {

    public static void main(String[] args) {
        int[] ints = {1,2,3,4,5};
        short[] shorts = new short[ints.length];
        System.arraycopy(ints, 0, shorts, 0, shorts.length);
        for (int i = 0; i < shorts.length; i++) {
            System.out.println(shorts[i]);
        }
    }
}
