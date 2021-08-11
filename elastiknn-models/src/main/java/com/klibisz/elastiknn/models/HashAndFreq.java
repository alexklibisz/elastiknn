package com.klibisz.elastiknn.models;

import java.util.Arrays;

/**
 * As the name suggests, represents a hash value and the number of the times it occurs in some context.
 * This enables LSH algorithms where the repetition of a hash has some significance.
 */
public class HashAndFreq implements Comparable<HashAndFreq> {
    public final int hash;      // Original int hash value.
    public final byte[] barr;   // The int hash value encoded as a byte array.
    public final int freq;      // Frequency, i.e., number of occurrences of this hash.

    public static HashAndFreq once(int hash) {
        return new HashAndFreq(hash, 1);
    }

    public static byte[] encode(int hash) {
        return new byte[] {
                (byte) (hash >>> 24),
                (byte) (hash >>> 16),
                (byte) (hash >>> 8),
                (byte) hash
        };
    }

    public HashAndFreq(int hash, int freq) {
        this.hash = hash;
        this.barr = encode(hash);
        this.freq = freq;
    }

    @Override
    public int compareTo(HashAndFreq o) {
        return Integer.compare(hash, o.hash);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HashAndFreq that = (HashAndFreq) o;
        return freq == that.freq && Arrays.equals(barr, that.barr);
    }

    @Override
    public int hashCode() {
        return 31 * freq + Arrays.hashCode(barr);
    }
}
