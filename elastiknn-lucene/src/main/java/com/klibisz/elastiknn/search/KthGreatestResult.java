package com.klibisz.elastiknn.search;

public record KthGreatestResult(short kthGreatest, int numGreaterThan) {


    @Override
    public String toString() {
        return String.format("KthGreatestResult(kthGreatest=%d, numGreaterThan=%d)", kthGreatest, numGreaterThan);
    }
}
