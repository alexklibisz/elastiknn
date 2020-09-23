package org.apache.lucene.search;

import com.klibisz.elastiknn.lucene.ArrayHitCounter;
import com.klibisz.elastiknn.lucene.ConstantHitCounter;
import com.klibisz.elastiknn.lucene.HitCounter;
import com.klibisz.elastiknn.models.HashAndFreq;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Query that finds docs containing the given hashes hashes (Lucene terms), and then applies a scoring function to the
 * docs containing the most matching hashes. Largely based on Lucene's TermsInSetQuery.
 */
public class MatchHashesAndScoreQuery extends Query {

    public interface ScoreFunction {
        double score(int docId, int numMatchingHashes);
    }

    private final String field;
    private final HashAndFreq[] hashAndFrequencies;
    private final int candidates;
    private final IndexReader indexReader;
    private final Function<LeafReaderContext, ScoreFunction> scoreFunctionBuilder;

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
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {

        return new Weight(this) {

            /**
             * Builds and returns a map from doc ID to the number of matching hashes in that doc.
             */
            private HitCounter countHits(LeafReader reader) throws IOException {
                Terms terms = reader.terms(field);
                // terms seem to be null after deleting docs. https://github.com/alexklibisz/elastiknn/issues/158
                if (terms == null) {
                    return new ArrayHitCounter(0);
                } else {
                    TermsEnum termsEnum = terms.iterator();
                    PostingsEnum docs = null;
                    // HitCounter counter = new ArrayHitCounter(reader.numDocs());
                    HitCounter counter = new ConstantHitCounter(reader.numDocs(), (short) 10);
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
            }

            private DocIdSetIterator buildDocIdSetIterator(HitCounter counter) {
                if (counter.isEmpty()) return DocIdSetIterator.empty();
                else {

                    KthGreatest.Result kgr = counter.kthGreatest(candidates);

                    // Return an iterator over the doc ids >= the min candidate count.
                    return new DocIdSetIterator() {

                        private int docId = -1;

                        private final HitCounter.Iterator iterator = counter.iterator();

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
                                if (numEmitted == candidates || !iterator.hasNext()) {
                                    docId = DocIdSetIterator.NO_MORE_DOCS;
                                    return docID();
                                }
                                iterator.advance();
                                if (iterator.count() > kgr.kthGreatest) {
                                    docId = iterator.docID();
                                    numEmitted++;
                                    return docID();
                                } else if (iterator.count() == kgr.kthGreatest && numEq < candidates - kgr.numGreaterThan) {
                                    docId = iterator.docID();
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
                            return counter.numHits();
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
                LeafReader reader = context.reader();
                HitCounter counter = countHits(reader);
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
                        return (float) scoreFunction.score(docID(), counter.get(docID()));
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
