package org.apache.lucene.search;

import com.carrotsearch.hppcrt.IntShortMap;
import com.klibisz.elastiknn.lucene.Counter;
import com.klibisz.elastiknn.models.HashAndFreq;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class MatchHashesAndScoreQuery extends Query {

    public interface ScoreFunction {
        double score(int docId, int numMatchingHashes);
    }

    private final String field;
    private final HashAndFreq[] hashAndFrequencies;
    private final int candidates;
    private final IndexReader indexReader;
    private final Function<LeafReaderContext, ScoreFunction> scoreFunctionBuilder;
    private final int numDocsInSegment;

    public MatchHashesAndScoreQuery(final String field,
                                    final HashAndFreq[] hashAndFrequencies,
                                    final int candidates,
                                    final IndexReader indexReader,
                                    final Function<LeafReaderContext, ScoreFunction> scoreFunctionBuilder) {
        // `countMatches` expects hashes to be in sorted order.
        // java's sort seems to be faster than lucene's ArrayUtil.
        java.util.Arrays.sort(hashAndFrequencies, HashAndFreq::compareTo);

        this.field = field;
        this.hashAndFrequencies = hashAndFrequencies;
        this.candidates = candidates;
        this.indexReader = indexReader;
        this.scoreFunctionBuilder = scoreFunctionBuilder;
        this.numDocsInSegment = indexReader.numDocs();
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {

        return new Weight(this) {

            /**
             * Builds and returns a map from doc ID to the number of matching hashes in that doc.
             */
            private Counter countMatches(LeafReaderContext context) throws IOException {
                LeafReader reader = context.reader();
                Terms terms = reader.terms(field);
                TermsEnum termsEnum = terms.iterator();
                PostingsEnum docs = null;
                Counter counter = new Counter(numDocsInSegment);
                for (HashAndFreq hac : hashAndFrequencies) {
                    if (termsEnum.seekExact(new BytesRef(hac.getHash()))) {
                        docs = termsEnum.postings(docs, PostingsEnum.NONE);
                        for (int i = 0; i < docs.cost(); i++) {
                            counter.increment(docs.nextDoc(), (short) Math.min(hac.getFreq(), docs.freq()));
                        }
                    }
                }
                return counter;
            }

            private DocIdSetIterator buildDocIdSetIterator(Counter counter) {
                if (candidates >= numDocsInSegment) return DocIdSetIterator.all(indexReader.maxDoc());
                else if (counter.isEmpty()) return DocIdSetIterator.empty();
                else {

                    Counter.KthGreatest kgr = counter.kthGreatest(candidates);

                    // Return an iterator over the doc ids >= the min candidate count.
                    return new DocIdSetIterator() {

                        private int docId = -1;
                        private int hitIndex = -1;

                        // Track the number of ids emitted, and the number of ids with count = kgr.kthGreatest emitted.
                        private int numEmitted = 0;
                        private int numEq = 0;

                        @Override
                        public int docID() {
                            return docId;
                        }

                        @Override
                        public int nextDoc() {

                            // Ensure that docs with count = kgr.kthGreatest are only emitted when there are fewer
                            // than `candidates` docs with count > kgr.kthGreatest.
                            while (true) {
                                if (numEmitted == candidates || hitIndex + 1 == counter.numHits) {
                                    docId = DocIdSetIterator.NO_MORE_DOCS;
                                    return docID();
                                }

                                docId = counter.hits[hitIndex++];
                                short count = counter.counts[docId];

                                if (count > kgr.kthGreatest) {
                                    numEmitted++;
                                    return docID();
                                } else if (count == kgr.kthGreatest && numEq < candidates - kgr.numGreaterThan) {
                                    numEq++;
                                    numEmitted++;
                                    return docID();
                                }
                            }

                        }

                        @Override
                        public int advance(int target) {
                            while (docId < target) nextDoc();
                            return docID();
                        }

                        @Override
                        public long cost() {
                            return counter.numHits;
                        }
                    };
                }
            }

            @Override
            public void extractTerms(Set<Term> terms) { }

            @Override
            public Explanation explain(LeafReaderContext context, int doc) {
                return Explanation.match( 0, "If someone knows what this should return, please submit a PR. :)");
            }

            @Override
            public Scorer scorer(LeafReaderContext context) throws IOException {

                ScoreFunction scoreFunction = scoreFunctionBuilder.apply(context);
                Counter counter = countMatches(context);
                DocIdSetIterator disi = buildDocIdSetIterator(counter);

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
                        return (float) scoreFunction.score(docID(), counter.counts[docID()]);
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
                this.hashAndFrequencies.length,
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
        return Objects.hash(field, hashAndFrequencies, candidates, indexReader, scoreFunctionBuilder);
    }
}
