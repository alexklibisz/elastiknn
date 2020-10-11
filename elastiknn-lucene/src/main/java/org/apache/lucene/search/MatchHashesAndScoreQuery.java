package org.apache.lucene.search;

import com.klibisz.elastiknn.models.HashAndFreq;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;
import org.javatuples.Pair;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static java.lang.Math.*;

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

                    int n = hashAndFrequencies.length;
                    int k = candidates;

                    TermsEnum termsEnum = terms.iterator();

                    // An array p of n posting lists, one per query term.
                    PostingsEnum[] p = new PostingsEnum[n];

                    // An array \sigma of n max score contributions, one per query term.
                    float[] sigma = new float[n];

                    // Term indices, sorted by \sigma value in ascending order.
                    int [] sortedIxs = new int[n];

                    // smallest docID from all postings.
                    int minDocID = Integer.MAX_VALUE;

                    // An array of n document upper bounds, one per posting list.
                    float[] ub = new float[n];

                    // Populate the above.
                    for (int i = 0; i < n; i++) {
                        sortedIxs[i] = i;
                        if (termsEnum.seekExact(new BytesRef(hashAndFrequencies[i].hash))) {
                            p[i] = termsEnum.postings(null, PostingsEnum.FREQS);
                            p[i].nextDoc(); // Advances the iterator.
                            minDocID = min(minDocID, p[i].docID());
                            sigma[i] = (float) log10(reader.maxDoc() * 1f / termsEnum.docFreq()) * hashAndFrequencies[i].freq;
                            ub[i] = ub[max(i - 1, 0)] + sigma[i];
                        }
                    }

                    // Sort the indices by sigma[i].
                    IntArrays.quickSort(sortedIxs, (i, j) -> Float.compare(sigma[i], sigma[j]));

                    // A priority queue of (at most) k (docID, score) pairs, sorted in decreasing order of score.
                    PriorityQueue<Pair<Integer, Float>> q = new PriorityQueue<>(k, (a, b) -> Float.compare(a.getValue1(), b.getValue1()));

                    float theta = 0f;
                    int pivot = 0;
                    int current = minDocID;

                    while (pivot < n && current != DocIdSetIterator.NO_MORE_DOCS) {
                        float score = 0;
                        int next = DocIdSetIterator.NO_MORE_DOCS;
                        // Essential lists.
                        for (int i = pivot; i < n; i++) {
                            if (p[i] != null) {
                                if (p[i].docID() == current) {
                                    score += sigma[i];
                                    p[i].nextDoc();
                                }
                                next = min(next, p[i].docID());
                            }
                        }
                        // Non-essential lists.
                        for (int i = pivot - 1; i > 0; i--) {
                            if (score + ub[i] <= theta) break;
                            else if (p[i] != null && p[i].docID() != DocIdSetIterator.NO_MORE_DOCS) {
                                p[i].advance(current);
                                if (p[i].docID() == current) {
                                    score += sigma[i];
                                }
                            }
                        }
                        // List pivot update.
                        if (q.size() < k) {
                            q.add(new Pair<>(current, score));
                            theta = q.peek().getValue1();
                            while (pivot < n && ub[pivot] <= theta) pivot++;
                        } else if (score > q.peek().getValue1()) {
                            q.remove();
                            q.add(new Pair<>(current, score));
                            theta = q.peek().getValue1();
                            while (pivot < n && ub[pivot] <= theta) pivot++;
                        }
                        // Advance the loop.
                        current = next;
                    }

                    Integer[] topDocIDs = new Integer[k];
                    Object[] oo = q.toArray();
                    for (int i = 0; i < k; i++) {
                        topDocIDs[i] = ((Pair<Integer, Float>) oo[i]).getValue0();
                    }

                    return topDocIDs;


//                    // Sort the sortedIxs based on tub in descending order.
//                    IntArrays.quickSort(sortedIxs, (i, j) -> Float.compare(tubs[j], tubs[i]));
//
//                    // Array of accumulators containing partial scores, indexed on docID.
//                    // Setting the accumulator to Float.MIN_VALUE indicates the docID is longer a candidate.
//                    float[] accumulators = new float[reader.maxDoc()];
//
//                    // Min-heap of top `candidates` docIDs with comparator using partial scores.
//                    PriorityQueue<Integer> topDocs = new PriorityQueue<>(candidates, Comparator.comparingDouble(i -> accumulators[i]));
//
//                    // Iterate the postings in sorted order, maintain partial scores and topDocs.
//                    // Check early stopping criteria after each postings list.
//                    for (int i = 0; i < sortedIxs.length; i++) {
//                        int ix = sortedIxs[i];
//                        float tub = tubs[ix];
//
//                        // Check early stopping.
//                        if (tub == 0) break;
//                        else if (topDocs.size() == candidates && tubSum <= accumulators[topDocs.peek()]) {
//                            logger.info(String.format(
//                                    "Early stopping at term [%d] of [%d], tub = [%f], tubSum = [%f], accumulators[topDocs.peek()] = [%f]",
//                                    i, sortedIxs.length, tubs[ix], tubSum, accumulators[topDocs.peek()]
//                            ));
//                            break;
//                        }
//
//                        // Process postings for this term.
//                        if (postings[ix] != null) {
//                            HashAndFreq hf = hashAndFrequencies[ix];
//                            PostingsEnum docs = postings[ix];
//
//                            int accumID = 0;
//                            while (docs.docID() != DocIdSetIterator.NO_MORE_DOCS && accumID < accumulators.length) {
//                                if (accumulators[accumID] < 0) {
//                                    accumID++;
//                                } else if (accumID < docs.docID()) {
//                                    accumID = docs.docID();
//                                } else if (accumID > docs.docID()) {
//                                    docs.advance(accumID);
//                                } else {
//                                    // Update accumulator score.
//                                    if (docs.freq() > hf.freq) accumulators[accumID] += tub;
//                                    else accumulators[accumID] += tub / hf.freq * docs.freq();
//                                    // Update the topDocs heap.
//                                    if (topDocs.size() < candidates) {
//                                        topDocs.add(docs.docID());
//                                    } else if (accumulators[topDocs.peek()] < accumulators[accumID]) {
//                                        topDocs.remove();
//                                        topDocs.add(accumID);
//                                    } else if (accumulators[topDocs.peek()] >= accumulators[accumID] + tubSum - tub) {
//                                        accumulators[accumID] = Float.MIN_VALUE;
//                                    }
//                                    // Move to the next accumulator. Cheaper than docs.nextDoc().
//                                    accumID++;
//                                }
//                            }
//
////                            while (docs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
////                                if (partials[docs.docID()] != Float.MIN_VALUE) {
////                                    if (docs.freq() > hf.freq) partials[docs.docID()] += tub;
////                                    else partials[docs.docID()] += tub / hf.freq * docs.freq();
////                                    if (topDocs.size() < candidates) {
////                                        topDocs.add(docs.docID());
////                                    } else if (partials[topDocs.peek()] < partials[docs.docID()]) {
////                                        topDocs.remove();
////                                        topDocs.add(docs.docID());
////                                    } else if (partials[topDocs.peek()] >= partials[docs.docID()] + tubSum - tub) {
////                                        logger.info(String.format("Cancelling [%d]", docs.docID()));
////                                        partials[docs.docID()] = Float.MIN_VALUE;
////                                    }
////                                }
////                            }
//
//
////                            for (int docID = 0; docID < partials.length; docID++) {
////                                if (partials[docID] != Float.MIN_VALUE && docs.docID() != DocIdSetIterator.NO_MORE_DOCS) {
////                                    int adv = docs.advance(docID);
////                                    logger.info(String.format("Visiting [%d], [%d]", docID, adv));
////                                    if (adv == docID) {
////                                        if (docs.freq() > hf.freq) partials[docID] += tub;
////                                        else partials[docID] += tub / hf.freq * docs.freq();
////                                        if (topDocs.size() < candidates) {
////                                            topDocs.add(docID);
////                                        } else if (partials[topDocs.peek()] < partials[docID]) {
////                                            topDocs.remove();
////                                            topDocs.add(docID);
////                                        } else if (partials[topDocs.peek()] >= partials[docID] + tubSum - tub) {
////                                            logger.info(String.format("Cancelling [%d]", docID));
////                                            partials[docID] = Float.MIN_VALUE;
////                                        }
////                                    }
////                                }
////                            }
//                        }
//
//                        // Decrement remaining sum of term upper-bounds.
//                        tubSum -= tub;
//                    }
//
//                    return topDocs.toArray(new Integer[0]);
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
