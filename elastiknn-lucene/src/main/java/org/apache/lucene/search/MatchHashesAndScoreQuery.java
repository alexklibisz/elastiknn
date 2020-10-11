package org.apache.lucene.search;

import com.klibisz.elastiknn.models.HashAndFreq;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatMaps;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;
import org.javatuples.Pair;

import java.awt.geom.FlatteningPathIterator;
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
        // java.util.Arrays.sort(hashAndFrequencies, HashAndFreq::compareTo);

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

                    int N = reader.numDocs();
                    int n = hashAndFrequencies.length;
                    int k = candidates;

                    PostingsEnum[] postings = new PostingsEnum[n];
                    float[] scores = new float[n];
                    for (int i = 0; i < n; i++) {
                        if (termsEnum.seekExact(new BytesRef(hashAndFrequencies[i].hash))) {
                            postings[i] = termsEnum.postings(null, PostingsEnum.FREQS);
                            scores[i] = (float) log10(N * 1f / termsEnum.docFreq()) * hashAndFrequencies[i].freq;
                        }
                    }

                    PriorityQueue<Pair<Integer, Float>> q = new PriorityQueue<>(k, (o1, o2) -> Float.compare(o1.getValue1(), o2.getValue1()));

                    int current = 0;
                    while (current != DocIdSetIterator.NO_MORE_DOCS) {
                        float score = 0;
                        int next = DocIdSetIterator.NO_MORE_DOCS;
                        for (int i = 0; i < n; i++) {
                            if (postings[i] == null) continue;
                            if (postings[i].docID() == current) {
                                score += scores[i];
                                postings[i].nextDoc();
                            }
                            if (postings[i].docID() < next) {
                                next = postings[i].docID();
                            }
                        }
                        if (q.size() < k) q.add(new Pair<>(current, score));
                        else if (q.peek().getValue1() < score) {
                            q.remove();
                            q.add(new Pair<>(current, score));
                        }
                        current = next;
                    }

                    Integer[] docIDs = new Integer[k];
                    Object[] objects = q.toArray();
                    for (int i = 0; i < docIDs.length; i++) {
                        docIDs[i] = ((Pair<Integer, Float>) objects[i]).getValue0();
                        // logger.info(docIDs[i]);
                    }
                    return docIDs;
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
