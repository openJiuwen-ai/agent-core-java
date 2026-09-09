/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.retrieval.vector_store.adapter;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.retrieval.vector_store.VectorStoreFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Foundation-store PGVector adapter.
 * 
 * @since 0.1.7
 */
public class PGVectorStore extends AbstractRetrievalVectorStoreAdapter {
    PGVectorStore(com.openjiuwen.retrieval.vector_store.PGVectorStore delegate) {
        super(delegate);
    }

    /**
     * PGVectorStore.
     * 
     * @param options options
     * @since 0.1.7
     */
    public PGVectorStore(Map<String, Object> options) {
        super(VectorStoreFactory.createVectorStore(config(options), withFoundationAliases(options)));
    }

    /**
     * config.
     * 
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private static VectorStoreConfig config(Map<String, Object> options) {
        return new VectorStoreConfig("pgvector",
                AdapterOptions.stringOption(options, "database_name", "databaseName", ""),
                AdapterOptions.stringOption(options, "collection_name", "collectionName", "default_collection"),
                AdapterOptions.stringOption(options, "distance_metric", "distanceMetric", "cosine"));
    }

    /**
     * withFoundationAliases.
     * 
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> withFoundationAliases(Map<String, Object> options) {
        Map<String, Object> isResolved = new LinkedHashMap<>();
        if (options != null) {
            isResolved.putAll(options);
        }
        isResolved.putIfAbsent("vector_field", "embedding");
        return isResolved;
    }
}
