package com.klibisz.elastiknn.vectors;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public final class CachedL2Distance {

    private final float[] query;

    private FloatVector[] chunks;

    private final VectorSpecies species;

    private final int bound;

    public CachedL2Distance(float[] query) {
        this.species = FloatVector.SPECIES_PREFERRED;
        this.bound = species.loopBound(query.length);
        this.query = query;
        this.chunks = new FloatVector[bound / species.length()];
        for (int i = 0; i < bound; i += species.length()) {
            this.chunks[i / species.length()] = FloatVector.fromArray(species, query, i);
        }
    }

    public double l2Distance(float[] stored) {
        double sumSqrDiff = 0f;
        int i = 0;
        int j = 0;
        FloatVector pStored, pDiff;
        for (; i < bound; i += species.length()) {
            pStored = FloatVector.fromArray(species, stored, i);
            pDiff = chunks[j].sub(pStored);
            sumSqrDiff += pDiff.mul(pDiff).reduceLanes(VectorOperators.ADD);
            j++;
        }
        for (; i < stored.length; i++) {
            float diff = query[i] - stored[i];
            sumSqrDiff += diff * diff;
        }
        return Math.sqrt(sumSqrDiff);
    }

}
