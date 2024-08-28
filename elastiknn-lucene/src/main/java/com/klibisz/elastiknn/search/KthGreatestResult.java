package com.klibisz.elastiknn.search;

public class KthGreatestResult {
    public final short kthGreatest;
    public final int numGreaterThan;
    public KthGreatestResult(short kthGreatest, int numGreaterThan) {
        this.kthGreatest = kthGreatest;
        this.numGreaterThan = numGreaterThan;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof KthGreatestResult other)) {
            return false;
        } else {
            return kthGreatest == other.kthGreatest && numGreaterThan == other.numGreaterThan;
        }
    }

    @Override
    public String toString() {
        return String.format("KthGreatestResult(kthGreatest=%d, numGreaterThan=%d)", kthGreatest, numGreaterThan);
    }
}
