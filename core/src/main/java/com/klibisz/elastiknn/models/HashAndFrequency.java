package com.klibisz.elastiknn.models;

import java.util.Arrays;
import java.util.Objects;

/**
 * As the name suggests, represents a hash value and the number of the times it occurs in some context.
 * This enables LSH algorithms where the repetition of a hash has some significance.
 */
public class HashAndFrequency implements Comparable<HashAndFrequency> {
    private final byte[] hash;
    private final int freq;

    public HashAndFrequency(byte[] hash) {
        this.hash = hash;
        this.freq = 1;
    }

    public HashAndFrequency(byte[] hash, int freq) {
        this.hash = hash;
        this.freq = freq;
    }

    public byte[] getHash() {
        return hash;
    }

    public int getFreq() {
        return freq;
    }

    @Override
    public int compareTo(HashAndFrequency o) {
        byte[] ohash = o.getHash();
        return Arrays.compareUnsigned(hash, 0, hash.length, ohash, 0, ohash.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HashAndFrequency that = (HashAndFrequency) o;
        return freq == that.freq &&
                Arrays.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(freq);
        result = 31 * result + Arrays.hashCode(hash);
        return result;
    }
}