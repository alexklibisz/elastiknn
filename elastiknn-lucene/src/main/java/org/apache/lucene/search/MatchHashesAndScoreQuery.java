package org.apache.lucene.search;

import com.klibisz.elastiknn.search.ArrayHitCounter;
import com.klibisz.elastiknn.search.HitCounter;
import com.klibisz.elastiknn.models.HashAndFreq;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
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

                    // Array of PostingsEnums, one for each term.
                    final PostingsEnum[] postingsEnums = new PostingsEnum[hashAndFrequencies.length];

                    // Total number of docs matching the query terms.
                    int docsRemaining = 0;

                    // Populate postingsEnums and docsRemaining.
                    for (int i = 0; i < hashAndFrequencies.length; i++) {
                        if (termsEnum.seekExact(new BytesRef(hashAndFrequencies[i].getHash()))) {
                            postingsEnums[i] = termsEnum.postings(null);
                            docsRemaining += termsEnum.docFreq();
                        }
                    }

                    // Number of query terms matched in each doc.
                    int[] counter = new int[reader.maxDoc()];

                    // Doc id of the last doc seen by each postings enum.
                    int[] lastSeen = new int[postingsEnums.length];

                    // Track the top k doc IDs. Note using counter for comparator.
                    PriorityQueue<Integer> minHeap = new PriorityQueue<>(candidates, Comparator.comparingInt(i -> counter[i]));
                    minHeap.add(0);

                    // Track the min doc id on each pass through the postings enums.
                    int minDocId = Integer.MAX_VALUE;

                    while (docsRemaining > 0) {

                        // Don't forget the previous min doc id.
                        int prevMinDocID = minDocId;
                        minDocId = Integer.MAX_VALUE;

                        // Iterate the postings and update the various state trackers.
                        for (int i = 0; i < postingsEnums.length; i++) {
                            PostingsEnum docs = postingsEnums[i];
                            if (docs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                docsRemaining -= 1;
                                minDocId = Math.min(minDocId, docs.docID());
                                counter[docs.docID()] += Math.min(hashAndFrequencies[i].getFreq(), docs.freq());
                                lastSeen[i] = docs.docID();
                            }
                        }

                        // Any doc id between the previous min and the current min can be added to the heap.
                        for (int i = prevMinDocID; i < minDocId; i++) {
                            if (minHeap.size() < candidates) minHeap.add(i);
                            else if (counter[minHeap.peek()] < counter[i]) {
                                minHeap.remove();
                                minHeap.add(i);
                            }
                        }

                        // Set the threshold, based on the count of the last doc id matched for each term.
                        int threshold = 0;
                        for (int docId : lastSeen) threshold += counter[docId];

                        // Early stopping.
                        if (minHeap.size() == candidates && minHeap.peek() > threshold) {
                            return minHeap.values;
                        }
                    }

                    return minHeap.values;

//                    termsEnum = terms.iterator();
//                    PostingsEnum docs = null;
//                    HitCounter counter = new ArrayHitCounter(reader.maxDoc());
//                    // TODO: Is this the right place to use the live docs bitset to check for deleted docs?
//                    // Bits liveDocs = reader.getLiveDocs();
//                    for (HashAndFreq hac : hashAndFrequencies) {
//                        if (termsEnum.seekExact(new BytesRef(hac.getHash()))) {
//                            docs = termsEnum.postings(docs, PostingsEnum.NONE);
//                            while (docs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
//                                counter.increment(docs.docID(), Math.min(hac.getFreq(), docs.freq()));
//                            }
//                        }
//                    }
//                    return counter;
                    return null;
                }
            }

            private DocIdSetIterator buildDocIdSetIterator(HitCounter counter) {
                if (counter.isEmpty()) return DocIdSetIterator.empty();
                else {

                    KthGreatest.Result kgr = counter.kthGreatest(candidates);

                    // Return an iterator over the doc ids >= the min candidate count.
                    return new DocIdSetIterator() {

                        private int docId = counter.minKey() - 1;

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
                                if (numEmitted == candidates || docId + 1 > counter.maxKey()) {
                                    docId = DocIdSetIterator.NO_MORE_DOCS;
                                    return docID();
                                } else {
                                    docId++;
                                    if (counter.get(docId) > kgr.kthGreatest) {
                                        numEmitted++;
                                        return docID();
                                    } else if (counter.get(docId) == kgr.kthGreatest && numEq < candidates - kgr.numGreaterThan) {
                                        numEq++;
                                        numEmitted++;
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
