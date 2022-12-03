package com.klibisz.elastiknn.lucene;

import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;

public final class HashFieldType extends FieldType {

    public HashFieldType() {
        setTokenized(false);
        setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        setOmitNorms(true);
        freeze();
    }

    public static HashFieldType HASH_FIELD_TYPE = new HashFieldType();
}
