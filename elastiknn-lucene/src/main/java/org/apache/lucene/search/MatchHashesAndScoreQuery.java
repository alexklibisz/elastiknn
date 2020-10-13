package org.apache.lucene.search;

import com.klibisz.elastiknn.search.ArrayHitCounter;
import com.klibisz.elastiknn.search.HitCounter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.klibisz.elastiknn.models.HashAndFreq;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static java.lang.StrictMath.*;

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
    private final Logger logger;
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
        this.logger = LogManager.getLogger(getClass().getName());
        this.scoreFunctionBuilder = scoreFunctionBuilder;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {

        return new Weight(this) {

            private int[] topKDocIDs(LeafReader reader) throws IOException {
                Terms terms = reader.terms(field);
                if (terms == null) return new int[0];
                else {
                    int N = reader.maxDoc();
                    int k = candidates;
                    TermsEnum termsEnum = terms.iterator();
                    PostingsEnum docs = null;
                    float[] scores = new float[N];
                    int minDocID = Integer.MAX_VALUE;
                    int maxDocID = Integer.MIN_VALUE;
                    for (HashAndFreq hf : hashAndFrequencies) {
                        if (termsEnum.seekExact(new BytesRef(hf.getHash()))) {
                            float idf = (float) log10(N * 1f / termsEnum.docFreq());
                            docs = termsEnum.postings(docs, PostingsEnum.FREQS);
                            while (docs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                scores[docs.docID()] += min(hf.getFreq(), docs.freq()) * idf;
                                minDocID = min(docs.docID(), minDocID);
                                maxDocID = max(docs.docID(), maxDocID);
                            }
                        }
                    }
                    PriorityQueue<Integer> q = new PriorityQueue<>(k, Comparator.comparingDouble(i -> scores[i]));
                    for (int docID = minDocID; docID <= maxDocID; docID++) {
                        if (q.size() < k) q.add(docID);
                        else if (scores[docID] > scores[q.peek()]) {
                            q.remove();
                            q.add(docID);
                        }
                    }
                    // logger.info(String.format("q.size() = %d", q.size()));
                    return q.stream().mapToInt(x -> x).toArray();
                }
            }

            private DocIdSetIterator buildDocIdSetIterator(int[] topDocIDs) {
                if (topDocIDs.length == 0) return DocIdSetIterator.empty();
                else {

                    // Lucene likes doc IDs in sorted order.
                    Arrays.sort(topDocIDs);

                    // Return an iterator over the doc ids >= the min candidate count.
                    return new DocIdSetIterator() {

                        private int i = -1;
                        private int docID = -1;

                        @Override
                        public int docID() {
                            return docID;
                        }

                        @Override
                        public int nextDoc() {
                            if (i + 1 == topDocIDs.length) {
                                docID = DocIdSetIterator.NO_MORE_DOCS;
                                return docID;
                            } else {
                                docID = topDocIDs[++i];
                                return docID;
                            }
                        }

                        @Override
                        public int advance(int target) {
                            while (docID < target) nextDoc();
                            return docID;
                        }

                        @Override
                        public long cost() {
                            return topDocIDs.length;
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
                int[] topDocIDs = topKDocIDs(reader);
                DocIdSetIterator disi = buildDocIdSetIterator(topDocIDs);

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
                        return (float) scoreFunction.score(docID(), 0);
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
