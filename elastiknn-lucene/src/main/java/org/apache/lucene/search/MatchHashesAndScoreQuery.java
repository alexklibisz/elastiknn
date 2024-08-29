package org.apache.lucene.search;

import com.klibisz.elastiknn.models.HashAndFreq;
import com.klibisz.elastiknn.search.*;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

import static java.lang.Math.max;
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
                    for (HashAndFreq hf : hashAndFrequencies) {
                        // We take two different paths here, depending on the frequency of the current hash.
                        // If the frequency is one, we avoid checking the frequency of matching docs when
                        // incrementing the counter. This yields a ~5% to ~10% speedup.
                        // See https://github.com/alexklibisz/elastiknn/pull/612 for details.
                        if (hf.freq == 1) {
                            if (termsEnum.seekExact(new BytesRef(hf.hash))) {
                                docs = termsEnum.postings(docs, PostingsEnum.NONE);
                                while (docs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                    counter.increment(docs.docID());
                                }
                            }
                        } else {
                            if (termsEnum.seekExact(new BytesRef(hf.hash))) {
                                docs = termsEnum.postings(docs, PostingsEnum.NONE);
                                while (docs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                    counter.increment(docs.docID(), (short) min(hf.freq, docs.freq()));
                                }
                            }
                        }
                    }
                    return counter;
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
                DocIdSetIterator disi = counter.docIdSetIterator(candidates);

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
