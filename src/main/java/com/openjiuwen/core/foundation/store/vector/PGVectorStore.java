/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.VectorStoreFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Foundation-store PGVector adapter.
 */
public class PGVectorStore extends AbstractRetrievalVectorStoreAdapter {

    public PGVectorStore(Map<String, Object> options) {
        super(VectorStoreFactory.createVectorStore(config(options), withFoundationAliases(options)));
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
}
