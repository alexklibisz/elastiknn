package org.apache.lucene.search;

import com.klibisz.elastiknn.utils.ArrayUtils;
import org.apache.lucene.index.*;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class MatchHashesAndScoreQuery extends Query {

    public interface ScoreFunction {
        double score(int docId, int numMatchingHashes);
    }

    private static class DocIdsArrayIterator extends DocIdSetIterator {

        private final int[] docIds;
        private int i = 0;

        public DocIdsArrayIterator(int[] docIds) {
            Arrays.sort(docIds);
            this.docIds = docIds;
        }

        @Override
        public int docID() {
            return docIds[i];
        }

        @Override
        public int nextDoc() {
            if (i == docIds.length - 1) return DocIdSetIterator.NO_MORE_DOCS;
            else {
                i += 1;
                return docID();
            }
        }

        @Override
        public int advance(int target) {
            if (target < docIds[0]) return docIds[0];
            else if (target > docIds[docIds.length - 1]) return DocIdSetIterator.NO_MORE_DOCS;
            else {
                while (docIds[i] < target) i += 1;
                return docID();
            }
        }

        @Override
        public long cost() {
            return docIds.length;
        }
    }

    private final String field;
    private final BytesRef[] hashes;
    private final int candidates;
    private final IndexReader indexReader;
    private final Function<LeafReaderContext, ScoreFunction> scoreFunctionBuilder;
    private final PrefixCodedTerms prefixCodedTerms;
    private final int numDocsInSegment;

    private static PrefixCodedTerms makePrefixCodedTerms(String field, BytesRef[] hashes) {
        // PrefixCodedTerms.Builder expects the hashes in sorted order.
        ArrayUtil.timSort(hashes);
        PrefixCodedTerms.Builder builder = new PrefixCodedTerms.Builder();
        for (BytesRef br : hashes) builder.add(field, br);
        return builder.finish();
    }

    public MatchHashesAndScoreQuery(final String field,
                                    final BytesRef[] hashes,
                                    final int candidates,
                                    final IndexReader indexReader,
                                    final Function<LeafReaderContext, ScoreFunction> scoreFunctionBuilder) {
        this.field = field;
        this.hashes = hashes;
        this.candidates = candidates;
        this.indexReader = indexReader;
        this.scoreFunctionBuilder = scoreFunctionBuilder;
        this.prefixCodedTerms = makePrefixCodedTerms(field, hashes);
        this.numDocsInSegment = indexReader.numDocs();
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {

        return new Weight(this) {

            private short[] countMatches(LeafReaderContext context) throws IOException {
                LeafReader reader = context.reader();
                Terms terms = reader.terms(field);
                TermsEnum termsEnum = terms.iterator();
                PrefixCodedTerms.TermIterator iterator = prefixCodedTerms.iterator();
                short[] counts = new short[numDocsInSegment];
                PostingsEnum docs = null;
                BytesRef term = iterator.next();
                while (term != null) {
                    if (termsEnum.seekExact(term)) {
                        docs = termsEnum.postings(docs, PostingsEnum.NONE);
                        for (int i = 0; i < docs.cost(); i++) {
                            int docId = docs.nextDoc();
                            counts[docId] += 1;
                        }
                    }
                    term = iterator.next();
                }
                return counts;
            }

            @Override
            public void extractTerms(Set<Term> terms) { }

            @Override
            public Explanation explain(LeafReaderContext context, int doc) {
                return null;
            }

            @Override
            public Scorer scorer(LeafReaderContext context) throws IOException {
                short[] counts = countMatches(context);
                int[] docIds = ArrayUtils.kLargestIndices(counts, candidates);
                DocIdsArrayIterator disi = new DocIdsArrayIterator(docIds);
                ScoreFunction scoreFunction = scoreFunctionBuilder.apply(context);
                return new Scorer(this) {
                    @Override
                    public DocIdSetIterator iterator() {
                        return disi;
                    }

                    @Override
                    public float getMaxScore(int upTo) {
                        return Float.MAX_VALUE;
                    }

                    @Override
                    public float score() {
                        return (float) scoreFunction.score(docID(), counts[docID()]);
                    }

                    @Override
                    public int docID() {
                        return disi.docID();
                    }
                };
            }

            @Override
            public boolean isCacheable(LeafReaderContext ctx) {
                return false;
            }
        };
    }

    @Override
    public String toString(String field) {
        return String.format(
                "%s for field [%s] with [%d] hashes and [%d] candidates",
                this.getClass().getSimpleName(),
                this.field,
                this.hashes.length,
                this.candidates);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MatchHashesAndScoreQuery) {
            MatchHashesAndScoreQuery q = (MatchHashesAndScoreQuery) obj;
            return q.hashCode() == this.hashCode();
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, hashes, candidates, indexReader, scoreFunctionBuilder);
    }
}
