 package org.apache.lucene.search;

import com.klibisz.elastiknn.search.ArrayHitCounter;
import com.klibisz.elastiknn.search.HitCounter;
import com.klibisz.elastiknn.models.HashAndFreq;
import com.klibisz.elastiknn.search.ShortMinHeap;
import com.klibisz.elastiknn.utils.Pair;
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
             * Builds and returns a Pair containing:
             * 1. A HitCounter mapping all doc IDs in this segment to the number of term matches in the doc.
             * 2. A heap of the top `candidates` term match counts.
             */
            private Pair<HitCounter, ShortMinHeap> countMatches(LeafReader reader) throws IOException {
                Terms terms = reader.terms(field);
                // terms seem to be null after deleting docs. https://github.com/alexklibisz/elastiknn/issues/158
                if (terms == null) {
                    return new Pair<>(new ArrayHitCounter(0), new ShortMinHeap(0));
                } else {
                    TermsEnum termsEnum = terms.iterator();

                    // Count the sum of docs matching the given terms.
                    int totalMatches = 0;
                    for (HashAndFreq hac : hashAndFrequencies) {
                        if (termsEnum.seekExact(new BytesRef(hac.getHash()))) {
                            totalMatches += termsEnum.docFreq();
                        }
                    }

                    // Use a priority queue to track the top `candidates` current counts.
                    ShortMinHeap countHeap = new ShortMinHeap(Math.min(candidates, reader.maxDoc()));

                    // Use an array to track the counts for docs in this segment.
                    HitCounter counter = new ArrayHitCounter(reader.maxDoc());

                    termsEnum = terms.iterator();
                    PostingsEnum doc = null;

                    for (HashAndFreq hf : hashAndFrequencies) {
                        if (termsEnum.seekExact(new BytesRef(hf.getHash()))) {
                            doc = termsEnum.postings(doc, PostingsEnum.FREQS);
                            while (doc.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                // Maintain counter.
                                counter.increment(doc.docID(), doc.freq());

                                // Maintain heap of top counts.
                                if (countHeap.size() < countHeap.capacity()) {
                                    countHeap.insert(counter.get(doc.docID()));
                                } else if (countHeap.peek() <= counter.get(doc.docID())) {
                                    countHeap.replace(counter.get(doc.docID()));
                                }

//                                // Check early-stopping condition.
//                                totalMatches -= doc.freq();
//                                if (counter.hits() >= candidates && countHeap.peek() > totalMatches) {
//                                    return new Pair<>(counter, countHeap);
//                                }
                            }
                        }
                    }

                    return new Pair<>(counter, countHeap);
                }
            }

            private DocIdSetIterator buildDocIdSetIterator(HitCounter hitCounter, ShortMinHeap countHeap) {
                if (hitCounter.isEmpty()) return DocIdSetIterator.empty();
                else {

                    short minCandidateCount = countHeap.peek();

                    // Return an iterator over the doc ids >= the min candidate count.
                    return new DocIdSetIterator() {

                        private int docId = -1;

                        // Track the number of ids emitted, and the number of ids with count = kgr.kthGreatest emitted.
                        private int numEmitted = 0;

                        @Override
                        public int docID() {
                            return docId;
                        }

                        @Override
                        public int nextDoc() {
                            while (true) {
                                if (numEmitted == candidates || docId + 1 == hitCounter.capacity()) {
                                    docId = DocIdSetIterator.NO_MORE_DOCS;
                                    return docID();
                                } else {
                                    docId++;
                                    if (hitCounter.get(docId) >= minCandidateCount) {
                                        numEmitted++;
                                        System.out.printf("%d, %d, %d\n", numEmitted, countHeap.capacity(), candidates);
                                        return docID();
                                    }
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
                            return hitCounter.capacity();
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
                Pair<HitCounter, ShortMinHeap> countResult = countMatches(reader);
                DocIdSetIterator disi = buildDocIdSetIterator(countResult.a, countResult.b);

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
                        return (float) scoreFunction.score(docID(), countResult.a.get(docID()));
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
