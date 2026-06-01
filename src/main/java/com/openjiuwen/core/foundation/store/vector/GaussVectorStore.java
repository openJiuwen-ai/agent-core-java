/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
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

    public GaussVectorStore(VectorStore delegate) {
        super(delegate);
    }

    public GaussVectorStore(Map<String, Object> options) {
        super(createDelegate(options));
    }

    private static VectorStore createDelegate(Map<String, Object> options) {
        Map<String, Object> resolved = withFoundationAliases(options);
        if (hasConnectionOptions(resolved)) {
            return VectorStoreFactory.createVectorStore(config(options), resolved);
        }
        return new com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore(
                new VectorStoreConfig(
                        "chroma",
                        InMemoryVectorStore.stringOption(options, "database_name", "databaseName", "default"),
                        InMemoryVectorStore.stringOption(options, "collection_name", "collectionName", "default_collection"),
                        InMemoryVectorStore.stringOption(options, "distance_metric", "distanceMetric", "cosine")
                ),
                InMemoryVectorStore.indexType(options)
        );
    }

    private static VectorStoreConfig config(Map<String, Object> options) {
        return new VectorStoreConfig(
                "pgvector",
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

    private static boolean hasConnectionOptions(Map<String, Object> options) {
        return options != null && (options.containsKey("dataSource")
                || options.containsKey("data_source")
                || options.containsKey("jdbcUrl")
                || options.containsKey("jdbc_url")
                || options.containsKey("pgUri")
                || options.containsKey("pg_uri")
                || options.containsKey("url"));
    }
}
