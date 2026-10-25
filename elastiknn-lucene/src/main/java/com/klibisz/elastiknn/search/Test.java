package com.klibisz.elastiknn.search;

public class Test {

    public static void main(String[] args) {
        byte[] b = new byte[3];
        b[0]++;
        b[0]++;
        b[0]+=5;
        System.out.println((int) (b[0] % 0xFF));
    }
}
