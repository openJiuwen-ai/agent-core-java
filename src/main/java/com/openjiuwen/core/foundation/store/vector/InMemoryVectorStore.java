/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import java.util.Map;

/**
 * Foundation-store in-memory vector store.
 */
public class InMemoryVectorStore extends AbstractRetrievalVectorStoreAdapter {

    public InMemoryVectorStore() {
        this(Map.of());
    }

    public InMemoryVectorStore(Map<String, Object> options) {
        super(new com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore(config("chroma", options), indexType(options)));
    }

    private static VectorStoreConfig config(String storeType, Map<String, Object> options) {
        return new VectorStoreConfig(
                storeType,
                stringOption(options, "database_name", "databaseName", "default"),
                stringOption(options, "collection_name", "collectionName", "default_collection"),
                stringOption(options, "distance_metric", "distanceMetric", "cosine")
        );
    }

    static String indexType(Map<String, Object> options) {
        return stringOption(options, "index_type", "indexType", "hybrid");
    }

    static String stringOption(Map<String, Object> options, String key, String altKey, String fallback) {
        Object value = options != null && options.containsKey(key) ? options.get(key)
                : options != null ? options.get(altKey) : null;
        return value == null ? fallback : String.valueOf(value);
    }
}
