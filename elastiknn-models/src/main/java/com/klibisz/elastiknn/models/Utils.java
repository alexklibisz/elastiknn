package com.klibisz.elastiknn.models;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public class Utils {

    public static float dot(float[] v1, float[] v2) {
//        float dp = 0f;
//        for (int i = 0; i < v1.length; i++) dp += v1[i] * v2[i];
//        return dp;
        return dotPanama(v1, v2);
    }

    private static VectorSpecies<Float> species = FloatVector.SPECIES_PREFERRED;

    public static float dotPanama(float[] v1, float[] v2) {
        float dp = 0f;
        int i = 0;
        int bound = species.loopBound(v1.length);
        VectorMask<Float> m;
        FloatVector l, r;
        for (; i < bound; i += species.length()) {
            l = FloatVector.fromArray(species, v1, i);
            r = FloatVector.fromArray(species, v2, i);
            dp += l.mul(r).reduceLanes(VectorOperators.ADD);
        }
        m = species.indexInRange(i, v1.length);
        l = FloatVector.fromArray(species, v1, i, m);
        r = FloatVector.fromArray(species, v2, i, m);
        return dp + l.mul(r).reduceLanes(VectorOperators.ADD);
    }

    /**
     * Compute the number of intersecting (i.e. identical) elements between two int arrays.
     * IMPORTANT: Assumes the given arrays are already sorted in ascending order and _does not_ check if this is true.
     * If the given arrays are not sorted, the answer will be wrong.
     * This is implemented in Java because for some reason Scala will Box the ints in some cases which is unnecessary
     * and far slower.
     * @param xs
     * @param ys
     * @return The number of identical elements in the two arrays. For example {1,2,3}, {2,3,4} would return 2.
     */
    public static int sortedIntersectionCount(final int [] xs, final int [] ys) {
        int n = 0;
        int xi = 0;
        int yi = 0;
        while (xi < xs.length && yi < ys.length) {
            int x = xs[xi];
            int y = ys[yi];
            if (x < y) xi += 1;
            else if (x > y) yi += 1;
            else {
                n += 1;
                xi += 1;
                yi += 1;
            }
        }
        return n;
    }

}
