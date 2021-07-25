package com.klibisz.elastiknn;

import com.klibisz.elastiknn.api4j.ElastiknnNearestNeighborsQuery;
import com.klibisz.elastiknn.api4j.Vector;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.SearchExecutionContext;

import java.io.IOException;
import java.util.Objects;

public class ElastiknnNearestNeighborsQueryBuilder extends AbstractQueryBuilder<ElastiknnNearestNeighborsQueryBuilder> {

    private final ElastiknnNearestNeighborsQuery query;
    private final String field;

    public ElastiknnNearestNeighborsQueryBuilder(ElastiknnNearestNeighborsQuery query, String field) {
        this.query = query;
        this.field = field;
    }

    @Override
    protected void doWriteTo(StreamOutput out) {
        throw new UnsupportedOperationException("doWriteTo is not implemented");
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(getWriteableName());
        builder.field("field", field);
        builder.field("similarity", query.getSimilarity().toString());
        if (query instanceof ElastiknnNearestNeighborsQuery.Exact) {
            builder.field("model", "exact");
        } else if (query instanceof ElastiknnNearestNeighborsQuery.CosineLsh) {
            ElastiknnNearestNeighborsQuery.CosineLsh q = (ElastiknnNearestNeighborsQuery.CosineLsh) query;
            builder.field("model", "lsh");
            builder.field("candidates", q.getCandidates());
        } else if (query instanceof ElastiknnNearestNeighborsQuery.L2Lsh) {
            ElastiknnNearestNeighborsQuery.L2Lsh q = (ElastiknnNearestNeighborsQuery.L2Lsh) query;
            builder.field("model", "lsh");
            builder.field("candidates", q.getCandidates());
            builder.field("probes", q.getProbes());
        } else if (query instanceof ElastiknnNearestNeighborsQuery.PermutationLsh) {
            ElastiknnNearestNeighborsQuery.PermutationLsh q = (ElastiknnNearestNeighborsQuery.PermutationLsh) query;
            builder.field("model", "permutation_lsh");
            builder.field("candidates", q.getCandidates());
        } else {
            throw new RuntimeException(String.format("Unexpected query type [%s]", query.getClass().toString()));
        }
        if (query.getVector() instanceof Vector.DenseFloat) {
            Vector.DenseFloat dfv = (Vector.DenseFloat) query.getVector();
            builder.field("vec", dfv.values);
        } else if (query.getVector() instanceof Vector.SparseBool) {
            Vector.SparseBool sbv = (Vector.SparseBool) query.getVector();
            builder.startArray("vec");
            builder.value(sbv.trueIndices);
            builder.value(sbv.totalIndices);
            builder.endArray();
        } else {
            throw new RuntimeException(String.format("Unexpected vector type [%s]", query.getVector().getClass().toString()));
        }
        builder.endObject();
    }

    @Override
    protected Query doToQuery(SearchExecutionContext context) {
        throw new UnsupportedOperationException("doToQuery is not implemented");
    }

    @Override
    protected boolean doEquals(ElastiknnNearestNeighborsQueryBuilder other) {
        return other != null && ((this == other) || (query.equals(other.query) && field.equals(other.field)));
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(query, field);
    }

    @Override
    public String getWriteableName() {
        return "elastiknn_nearest_neighbors";
    }
}
