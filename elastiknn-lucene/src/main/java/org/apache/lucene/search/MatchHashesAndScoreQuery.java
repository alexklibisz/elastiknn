package org.apache.lucene.search;

import com.klibisz.elastiknn.models.HashAndFreq;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatMaps;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static java.lang.Math.log10;

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
        // `countHits` expects hashes to be in sorted order.
        // java's sort seems to be faster than lucene's ArrayUtil.
        java.util.Arrays.sort(hashAndFrequencies, HashAndFreq::compareTo);

        this.field = field;
        this.hashAndFrequencies = hashAndFrequencies;
        this.candidates = candidates;
        this.indexReader = indexReader;
        this.scoreFunctionBuilder = scoreFunctionBuilder;
        this.logger = LogManager.getLogger(this.getClass().getName());
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {

        return new Weight(this) {

            /**
             * Build and return an array of the top `candidates` docIDs.
             */
            private Integer[] countHits(LeafReader reader) throws IOException {
                Terms terms = reader.terms(field);
                // terms seem to be null after deleting docs. https://github.com/alexklibisz/elastiknn/issues/158
                if (terms == null) return new Integer[0];
                else {

                    TermsEnum termsEnum = terms.iterator();
                    int N = reader.maxDoc();
                    int n = hashAndFrequencies.length;
                    int k = candidates;

                    // Array of postings, one per term.
                    PostingsEnum[] postings = new PostingsEnum[n];

                    // Array of term upper-bounds, one per term, and sum of them.
                    float[] tubs = new float[n];
                    float tubSum = 0;

                    // Indices into hashAndFrequences, postings, tubs. Will be sorted after populating.
                    int[] sortedIxs = new int[n];

                    // Populate postings, tubs, tubSum, sortedIxs.
                    for (int i = 0; i < n; i++) {
                        sortedIxs[i] = i;
                        if (termsEnum.seekExact(new BytesRef(hashAndFrequencies[i].hash))) {
                            tubs[i] = (float) log10(N * 1.0 / termsEnum.docFreq());
                            tubSum += tubs[i];
                            postings[i] = termsEnum.postings(null, PostingsEnum.NONE);
                        }
                    }

                    // Sort the sortedIxs based on tub in descending order.
                    IntArrays.quickSort(sortedIxs, (i, j) -> Float.compare(tubs[j], tubs[i]));

                    // Array of (partial) scores, one per doc.
                    float[] partials = new float[reader.maxDoc()];

                    // Min-heap of top `candidates` docIDs with comparator using partial scores.
                    PriorityQueue<Integer> topDocs = new PriorityQueue<>(k, Comparator.comparingDouble(i -> partials[i]));

                    // Iterate the postings in sorted order, maintain partial scores and topDocs.
                    // Check early stopping criteria after each postings list.
                    for (int i = 0; i < sortedIxs.length; i++) {
                        int ix = sortedIxs[i];
                        float tub = tubs[ix];

                        // Check early stopping.
                        if (tub == 0 || (topDocs.size() == k && tubSum <= partials[topDocs.peek()])) {
//                            logger.info(String.format(
//                                    "Early stopping at term [%d] of [%d], tub = [%f], tubSum = [%f], partials[topDocs.peek()] = [%f]",
//                                    i, sortedIxs.length, tubs[ix], tubSum, partials[topDocs.peek()]
//                            ));
                            break;
                        }

                        // Process postings for this term.
                        PostingsEnum docs = postings[ix];
                        while (docs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                            if (partials[docs.docID()] >= 0) {
                                partials[docs.docID()] += tub;
                                if (topDocs.size() < k) {
                                    topDocs.add(docs.docID());
                                } else if (partials[topDocs.peek()] < partials[docs.docID()]) {
                                    topDocs.remove();
                                    topDocs.add(docs.docID());
                                } else if (partials[topDocs.peek()] >= partials[docs.docID()] - tub + tubSum) {
                                    partials[docs.docID()] = Float.MIN_VALUE;
                                }
                            }
                        }

                        // Decrement remaining sum of term upper-bounds.
                        tubSum -= tub;
                    }

                    return topDocs.toArray(new Integer[0]);
                }
            }

            private DocIdSetIterator buildDocIdSetIterator(Integer[] topDocIDs) {
                if (topDocIDs.length == 0) return DocIdSetIterator.empty();
                else {

                    // Lucene likes doc IDs in sorted order.
                    Arrays.sort(topDocIDs);

                    // Return an iterator over the doc ids >= the min candidate count.
                    return new DocIdSetIterator() {

                        private int i = -1;
                        private int docID = topDocIDs[0];

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
                Integer[] topDocIDs = countHits(reader);
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
