package org.apache.lucene.search;

import com.klibisz.elastiknn.search.ArrayHitCounter;
import com.klibisz.elastiknn.search.HitCounter;
import com.klibisz.elastiknn.models.HashAndFreq;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
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

    public interface HitsCache {

        class Key {
            final BytesRef term;
            final int docFreq;
            public Key(BytesRef term, int docFreq) {
                this.term = term;
                this.docFreq = docFreq;
            }
        }

        class Value {
            final int docId;
            final int freq;
            public Value(int id, int freq) {
                this.docId = id;
                this.freq = freq;
            }
        }

        Optional<Value[]> get(Key key);
        void set(Key key, Value[] values);
    }

    private final String field;
    private final HashAndFreq[] hashAndFrequencies;
    private final int candidates;
    private final IndexReader indexReader;
    private final Function<LeafReaderContext, ScoreFunction> scoreFunctionBuilder;
    private final Logger logger;
    private final Optional<HitsCache> hitsCacheOpt;

    public MatchHashesAndScoreQuery(final String field,
                                    final HashAndFreq[] hashAndFrequencies,
                                    final int candidates,
                                    final IndexReader indexReader,
                                    final Function<LeafReaderContext, ScoreFunction> scoreFunctionBuilder,
                                    final Optional<HitsCache> hitsCacheOpt) {
        // `countMatches` expects hashes to be in sorted order.
        // java's sort seems to be faster than lucene's ArrayUtil.
        java.util.Arrays.sort(hashAndFrequencies, HashAndFreq::compareTo);

        this.field = field;
        this.hashAndFrequencies = hashAndFrequencies;
        this.candidates = candidates;
        this.indexReader = indexReader;
        this.scoreFunctionBuilder = scoreFunctionBuilder;
        this.hitsCacheOpt = hitsCacheOpt;
        this.logger = LogManager.getLogger(this.getClass().getName());
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

                    HitCounter counter = new ArrayHitCounter(reader.maxDoc());

                    // TODO: Is this the right place to use the live docs bitset to check for deleted docs?
                    // Bits liveDocs = reader.getLiveDocs();

                    for (HashAndFreq hac : hashAndFrequencies) {
                        BytesRef term = new BytesRef(hac.getHash());
                        if (termsEnum.seekExact(term)) {
                            // Yes caching.
                            if (hitsCacheOpt.isPresent()) {
                                HitsCache.Key key = new HitsCache.Key(term, termsEnum.docFreq());
                                Optional<HitsCache.Value[]> valuesOpt = hitsCacheOpt.get().get(key);
                                // Populate the cache for this term and update the counter.
                                if (!valuesOpt.isPresent()) {
                                    HitsCache.Value[] values = new HitsCache.Value[termsEnum.docFreq()];
                                    docs = termsEnum.postings(docs, PostingsEnum.NONE);
                                    int vix = 0;
                                    while (docs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                        values[vix++] = new HitsCache.Value(docs.docID(), docs.freq());
                                        counter.increment(docs.docID(), Math.min(hac.getFreq(), docs.freq()));
                                    }
                                    hitsCacheOpt.get().set(key, values);
                                }
                                // Read the terms from the cache and update the counter.
                                else {
                                    for (HitsCache.Value value : valuesOpt.get()) {
                                        counter.increment(value.docId, Math.min(hac.getFreq(), value.freq));
                                    }
                                }
                            }
                            // No caching.
                            else {
                                docs = termsEnum.postings(docs, PostingsEnum.NONE);
                                while (docs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
                                    counter.increment(docs.docID(), Math.min(hac.getFreq(), docs.freq()));
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
