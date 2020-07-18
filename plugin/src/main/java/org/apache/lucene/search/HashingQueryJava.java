package org.apache.lucene.search;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIntScatterMap;
import com.carrotsearch.hppc.cursors.IntIntCursor;
import com.klibisz.elastiknn.utils.ArrayUtils;
import org.apache.lucene.index.*;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class HashingQueryJava extends Query {

    public interface ScoreFunction {
        double score(int docId, int numMatchingHashes);
    }

    private static class DocIdsArrayIterator extends DocIdSetIterator {

        private final int[] docIds;
        private int i = 0;

        public DocIdsArrayIterator(int[] docIds) {
            Arrays.sort(docIds);
            this.docIds = docIds;
        }

        @Override
        public int docID() {
            return docIds[i];
        }

        @Override
        public int nextDoc() {
            if (i == docIds.length - 1) return DocIdSetIterator.NO_MORE_DOCS;
            else {
                i += 1;
                return docID();
            }
        }

        @Override
        public int advance(int target) throws IOException {
            if (target < docIds[0]) return docIds[0];
            else if (target > docIds[docIds.length - 1]) return DocIdSetIterator.NO_MORE_DOCS;
            else {
                while (docIds[i] < target) i += 1;
                return docID();
            }
        }

        @Override
        public long cost() {
            return docIds.length;
        }
    }

    private final String field;
    private final BytesRef[] hashes;
    private final int candidates;
    private final IndexReader indexReader;
    private final Function<LeafReaderContext, ScoreFunction> scoreFunctionBuilder;
    private final PrefixCodedTerms prefixCodedTerms;
    private final int expectedMatchingDocs;

    private static PrefixCodedTerms makePrefixCodedTerms(String field, BytesRef[] hashes) {
        // TODO: ensure the hashes are prefixed with sorted tokens so that the sort is unnecessary.
        ArrayUtil.timSort(hashes);
        PrefixCodedTerms.Builder builder = new PrefixCodedTerms.Builder();
        for (BytesRef br : hashes) builder.add(field, br);
        return builder.finish();
    }

    public HashingQueryJava(final String field,
                            final BytesRef[] hashes,
                            final int candidates,
                            final IndexReader indexReader,
                            final Function<LeafReaderContext, ScoreFunction> scoreFunctionBuilder) throws IOException {
        this.field = field;
        this.hashes = hashes;
        this.candidates = candidates;
        this.indexReader = indexReader;
        this.scoreFunctionBuilder = scoreFunctionBuilder;
        this.prefixCodedTerms = makePrefixCodedTerms(field, hashes);

        // Rough prediction for the number of (docId, count) pairs that will be kept in memory.
        int numSegments = indexReader.getContext().leaves().size();
        this.expectedMatchingDocs = Math.min(130000, (indexReader.getDocCount(field) / numSegments));
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        return new Weight(this) {

            private IntIntScatterMap countMatches(LeafReaderContext context) throws IOException {
                LeafReader reader = context.reader();
                Terms terms = reader.terms(field);
                TermsEnum termsEnum = terms.iterator();
                PrefixCodedTerms.TermIterator iterator = prefixCodedTerms.iterator();
                IntIntScatterMap docToCount = new IntIntScatterMap(expectedMatchingDocs);
                PostingsEnum docs = null;
                BytesRef term = iterator.next();
                while (term != null) {
                    if (termsEnum.seekExact(term)) {
                        docs = termsEnum.postings(docs, PostingsEnum.NONE);
                        for (int i = 0; i < docs.cost(); i ++) {
                            int docId = docs.nextDoc();
                            docToCount.putOrAdd(docId, 1, 1);
                        }
                    }
                    term = iterator.next();
                }
                return docToCount;
            }

            private int[] pickCandidates(IntIntScatterMap counts) {
                if (counts.size() <= candidates) return counts.keys().toArray();
                else {
                    int minCount = ArrayUtils.quickSelectCopy(counts.values, candidates);
                    IntArrayList docIds = new IntArrayList(candidates * 11 / 10);
                    counts.forEach((Consumer<IntIntCursor>) c -> {
                        if (c.value >= minCount) docIds.add(c.key);
                    });
                    return docIds.toArray();
                }
            }

            @Override
            public void extractTerms(Set<Term> terms) { }

            @Override
            public Explanation explain(LeafReaderContext context, int doc) throws IOException {
                return null;
            }

            @Override
            public Scorer scorer(LeafReaderContext context) throws IOException {
                IntIntScatterMap matches = countMatches(context);
                int[] candidates = pickCandidates(matches);
                DocIdsArrayIterator disi = new DocIdsArrayIterator(candidates);
                ScoreFunction scoreFunction = scoreFunctionBuilder.apply(context);
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
                        return (float) scoreFunction.score(docID(), matches.get(docID()));
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
                this.hashes.length,
                this.candidates);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HashingQueryJava) {
            HashingQueryJava q = (HashingQueryJava) obj;
            return q.hashCode() == this.hashCode();
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, hashes, candidates, indexReader, scoreFunctionBuilder);
    }
}
