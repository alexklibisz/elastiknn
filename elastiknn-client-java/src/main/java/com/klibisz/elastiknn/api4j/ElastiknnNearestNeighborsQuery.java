package com.klibisz.elastiknn.api4j;

import java.util.Objects;

public abstract class ElastiknnNearestNeighborsQuery {

    private ElastiknnNearestNeighborsQuery() {}

    public abstract Vector getVector();
    public abstract Similarity getSimilarity();

    public static final class Exact extends ElastiknnNearestNeighborsQuery {
        private final Similarity similarity;
        private final Vector vector;
        public Exact(Vector vector, Similarity similarity) {
            this.similarity = similarity;
            this.vector = vector;
        }

        @Override
        public Vector getVector() {
            return vector;
        }

        @Override
        public Similarity getSimilarity() {
            return similarity;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Exact exact = (Exact) o;
            return getSimilarity() == exact.getSimilarity() && Objects.equals(getVector(), exact.getVector());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getSimilarity(), getVector());
        }
    }

    public static final class AngularLsh extends ElastiknnNearestNeighborsQuery {
        private final Vector vector;
        private final Integer candidates;
        private final Similarity similarity;
        public AngularLsh(Vector vector, Integer candidates) {
            this.vector = vector;
            this.candidates = candidates;
            this.similarity = Similarity.ANGULAR;
        }

        public Integer getCandidates() {
            return candidates;
        }

        @Override
        public Vector getVector() {
            return vector;
        }

        @Override
        public Similarity getSimilarity() {
            return similarity;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AngularLsh that = (AngularLsh) o;
            return Objects.equals(getVector(), that.getVector()) && Objects.equals(getCandidates(), that.getCandidates()) && getSimilarity() == that.getSimilarity();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getVector(), getCandidates(), getSimilarity());
        }
    }

    public static final class L2Lsh extends ElastiknnNearestNeighborsQuery {
        private final Vector.DenseFloat vector;
        private final Integer candidates;
        private final Integer probes;
        private final Similarity similarity;
        public L2Lsh(Vector.DenseFloat vector, Integer candidates, Integer probes) {
            this.vector = vector;
            this.candidates = candidates;
            this.probes = probes;
            this.similarity = Similarity.L2;
        }

        public Integer getProbes() {
            return probes;
        }

        public Integer getCandidates() {
            return candidates;
        }

        @Override
        public Vector getVector() {
            return vector;
        }

        @Override
        public Similarity getSimilarity() {
            return similarity;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            L2Lsh l2Lsh = (L2Lsh) o;
            return Objects.equals(getVector(), l2Lsh.getVector()) && Objects.equals(getCandidates(), l2Lsh.getCandidates()) && Objects.equals(getProbes(), l2Lsh.getProbes()) && getSimilarity() == l2Lsh.getSimilarity();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getVector(), getCandidates(), getProbes(), getSimilarity());
        }
    }

    public final static class PermutationLsh extends ElastiknnNearestNeighborsQuery {
        private final Vector.DenseFloat vector;
        private final Similarity similarity;
        private final Integer candidates;
        public PermutationLsh(Vector.DenseFloat vector, Similarity similarity, Integer candidates) {
            this.vector = vector;
            this.similarity = similarity;
            this.candidates = candidates;
        }

        public Integer getCandidates() {
            return candidates;
        }

        @Override
        public Vector getVector() {
            return vector;
        }

        @Override
        public Similarity getSimilarity() {
            return similarity;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PermutationLsh that = (PermutationLsh) o;
            return Objects.equals(getVector(), that.getVector()) && getSimilarity() == that.getSimilarity() && Objects.equals(getCandidates(), that.getCandidates());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getVector(), getSimilarity(), getCandidates());
        }
    }
}