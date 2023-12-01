package com.klibisz.elastiknn.search;

public class KthGreatestResult {
    public final short kthGreatest;
    public final int numGreaterThan;
    public final int numNonZero;
    public KthGreatestResult(short kthGreatest, int numGreaterThan, int numNonZero) {
        this.kthGreatest = kthGreatest;
        this.numGreaterThan = numGreaterThan;
        this.numNonZero = numNonZero;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof KthGreatestResult other)) {
            return false;
        } else {
            return kthGreatest == other.kthGreatest && numGreaterThan == other.numGreaterThan && numNonZero == other.numNonZero;
        }
    }

    @Override
    public String toString() {
        return String.format("KthGreatestResult(kthGreatest=%d, numGreaterThan=%d, numNonZero=%d)", kthGreatest, numGreaterThan, numNonZero);
    }
}
