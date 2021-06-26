package com.klibisz.elastiknn.api4j;

import java.util.Arrays;
import java.util.Objects;

public abstract class Vector {

    private Vector() {}

    public static final class DenseFloat extends Vector {
        public final float[] values;
        public DenseFloat(float[] values) {
            this.values = values;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DenseFloat that = (DenseFloat) o;
            return Arrays.equals(values, that.values);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(values);
        }
    }

    public static final class SparseBool extends Vector {
        public final int[] trueIndices;
        public final Integer totalIndices;
        public SparseBool(int[] trueIndices, Integer totalIndices) {
            this.trueIndices = trueIndices;
            this.totalIndices = totalIndices;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SparseBool that = (SparseBool) o;
            return Arrays.equals(trueIndices, that.trueIndices) && Objects.equals(totalIndices, that.totalIndices);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(totalIndices);
            result = 31 * result + Arrays.hashCode(trueIndices);
            return result;
        }
    }
}
