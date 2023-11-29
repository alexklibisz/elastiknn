package org.apache.lucene.search;

import com.klibisz.elastiknn.models.HashAndFreq;
import com.klibisz.elastiknn.search.ArrayHitCounter;
import com.klibisz.elastiknn.search.EmptyHitCounter;
import com.klibisz.elastiknn.search.HitCounter;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static java.lang.Math.min;

/**
 * Query that finds docs containing the given hashes (Lucene terms), and then applies a scoring function to the
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
                    return new EmptyHitCounter();
                } else {
                    TermsEnum termsEnum = terms.iterator();
                    PostingsEnum docs = null;
                    HitCounter counter = new ArrayHitCounter(reader.maxDoc());
                    // TODO: Is this the right place to use the live docs bitset to check for deleted docs?
                    // Bits liveDocs = reader.getLiveDocs();
                    for (HashAndFreq hf : hashAndFrequencies) {
                        if (termsEnum.seekExact(new BytesRef(hf.hash))) {
                            docs = termsEnum.postings(docs, PostingsEnum.NONE);
                            while (docs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                counter.increment(docs.docID(), min(hf.freq, docs.freq()));
                            }
                        }
                    }
                    return counter;
                }
            }

            private DocIdSetIterator buildDocIdSetIterator(HitCounter counter) {
                // TODO: Add back this logging once log4j mess has settled.
//                if (counter.numHits() < candidates) {
//                logger.warn(String.format(
//                        "Found fewer approximate matches [%d] than the requested number of candidates [%d]",
//                        counter.numHits(), candidates));
//                }
                if (counter.isEmpty()) return DocIdSetIterator.empty();
                else {

                    KthGreatest.Result kgr = counter.kthGreatest(candidates);

                    // Return an iterator over the doc ids >= the min candidate count.
                    return new DocIdSetIterator() {

                        // Important that this starts at -1. Need a boolean to denote that it has started iterating.
                        private int docID = -1;
                        private boolean started = false;

                        // Track the number of ids emitted, and the number of ids with count = kgr.kthGreatest emitted.
                        private int numEmitted = 0;
                        private int numEq = 0;

                        @Override
                        public int docID() {
                            return docID;
                        }

                        @Override
                        public int nextDoc() {

                            if (!started) {
                                started = true;
                                docID = counter.minKey() - 1;
                            }

                            // Ensure that docs with count = kgr.kthGreatest are only emitted when there are fewer
                            // than `candidates` docs with count > kgr.kthGreatest.
                            while (true) {
                                if (numEmitted == candidates || docID + 1 > counter.maxKey()) {
                                    docID = DocIdSetIterator.NO_MORE_DOCS;
                                    return docID();
                                } else {
                                    docID++;
                                    if (counter.get(docID) > kgr.kthGreatest) {
                                        numEmitted++;
                                        return docID();
                                    } else if (counter.get(docID) == kgr.kthGreatest && numEq < candidates - kgr.numGreaterThan) {
                                        numEq++;
                                        numEmitted++;
                                        return docID();
                                    }
                                }
                            }
                        }

                        @Override
                        public int advance(int target) {
                            while (docID < target) nextDoc();
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
            public Explanation explain(LeafReaderContext context, int doc) throws IOException {
                HitCounter counter = countHits(context.reader());
                if (counter.get(doc) > 0) {
                    ScoreFunction scoreFunction = scoreFunctionBuilder.apply(context);
                    double score = scoreFunction.score(doc, counter.get(doc));
                    return Explanation.match(score, String.format("Document [%d] and the query vector share [%d] of [%d] hashes. Their exact similarity score is [%f].", doc, counter.get(doc), hashAndFrequencies.length, score));
                } else {
                    return Explanation.noMatch(String.format("Document [%d] and the query vector share no common hashes.", doc));
                }
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
                        int docID = docID();
                        // TODO: how does it get to this state? This error did come up once in some local testing.
                        if (docID == DocIdSetIterator.NO_MORE_DOCS) return 0f;
                        else return (float) scoreFunction.score(docID, counter.get(docID));
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
    public void visit(QueryVisitor visitor) {

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
        return Objects.hash(field, Arrays.hashCode(hashAndFrequencies), candidates, indexReader, scoreFunctionBuilder);
    }
}
