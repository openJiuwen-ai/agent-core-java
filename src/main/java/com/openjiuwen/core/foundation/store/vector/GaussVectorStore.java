/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.VectorStoreFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Foundation-store GaussVector adapter.
 * <p>
 * Mirrors Python's {@code GaussVectorStore} in
 * {@code foundation/store/vector/gauss_vector_store.py}.
 * <p>
 * GaussVector is a PostgreSQL-compatible vector database. This adapter follows
 * the same pattern as {@link PGVectorStore}, delegating to the retrieval layer
 * via {@link VectorStoreFactory}.
 */
public class GaussVectorStore extends AbstractRetrievalVectorStoreAdapter {

    public GaussVectorStore(Map<String, Object> options) {
        super(VectorStoreFactory.createVectorStore(config(options), withFoundationAliases(options)));
    }

    private static VectorStoreConfig config(Map<String, Object> options) {
        return new VectorStoreConfig(
                "gauss",
                InMemoryVectorStore.stringOption(options, "database_name", "databaseName", ""),
                InMemoryVectorStore.stringOption(options, "collection_name", "collectionName", "default_collection"),
                InMemoryVectorStore.stringOption(options, "distance_metric", "distanceMetric", "cosine")
        );
    }

    private static Map<String, Object> withFoundationAliases(Map<String, Object> options) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        if (options != null) {
            resolved.putAll(options);
        }
        resolved.putIfAbsent("vector_field", "embedding");
        return resolved;
    }
}
