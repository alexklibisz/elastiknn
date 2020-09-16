package org.apache.lucene.search;

import com.carrotsearch.hppcrt.IntShortMap;
import com.carrotsearch.hppcrt.cursors.IntShortCursor;
import com.carrotsearch.hppcrt.maps.IntShortHashMap;
import com.klibisz.elastiknn.models.HashAndFreq;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Iterator;
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
            private IntShortMap countMatches(LeafReaderContext context) throws IOException {
                LeafReader reader = context.reader();
                Terms terms = reader.terms(field);
                TermsEnum termsEnum = terms.iterator();
                PostingsEnum docs = null;
                IntShortMap counts = new IntShortHashMap(candidates * 3 / 2);
                for (HashAndFreq hac : hashAndFrequencies) {
                    if (termsEnum.seekExact(new BytesRef(hac.getHash()))) {
                        docs = termsEnum.postings(docs, PostingsEnum.NONE);
                        for (int i = 0; i < docs.cost(); i++) {
                            int docId = docs.nextDoc();
                            short incr = (short) Math.min(hac.getFreq(), docs.freq());
                            counts.putOrAdd(docId, incr, incr);
                        }
                    }
                }
                return counts;
            }

            private DocIdSetIterator buildDocIdSetIterator(IntShortMap counts) {
                if (candidates >= numDocsInSegment) return DocIdSetIterator.all(indexReader.maxDoc());
                else if (counts.isEmpty()) return DocIdSetIterator.empty();
                else {
                    // Compute the kth greatest count to use as a lower bound for picking candidates.
                    KthGreatest.KthGreatestResult kgr = KthGreatest.kthGreatest(counts.values().toArray(), candidates);

                    // Return an iterator over the doc ids >= the min candidate count.
                    return new DocIdSetIterator() {

                        private final Iterator<IntShortCursor> countsIter = counts.iterator();

                        private int docId = -1;

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
                                if (numEmitted == candidates || !countsIter.hasNext()) {
                                    docId = DocIdSetIterator.NO_MORE_DOCS;
                                    return docID();
                                }
                                IntShortCursor countsCursor = countsIter.next();
                                if (countsCursor.value > kgr.kthGreatest) {
                                    numEmitted++;
                                    docId = countsCursor.key;
                                    return docID();
                                } else if (countsCursor.value == kgr.kthGreatest && numEq < candidates - kgr.numGreaterThanKthGreatest) {
                                    numEq++;
                                    numEmitted++;
                                    docId = countsCursor.key;
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
                            return counts.size();
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
                IntShortMap counts = countMatches(context);
                DocIdSetIterator disi = buildDocIdSetIterator(counts);

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
                        return (float) scoreFunction.score(docID(), counts.get(docID()));
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
