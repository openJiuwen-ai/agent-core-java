/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import java.util.Map;

/**
 * Foundation-store PGVector adapter.
 */
public class PGVectorStore extends AbstractRetrievalVectorStoreAdapter {

    public PGVectorStore(Map<String, Object> options) {
        super(new com.openjiuwen.core.retrieval.vector_store.PGVectorStore(
                new VectorStoreConfig(
                        "pgvector",
                        InMemoryVectorStore.stringOption(options, "database_name", "databaseName", "default"),
                        InMemoryVectorStore.stringOption(options, "collection_name", "collectionName", "default_collection"),
                        InMemoryVectorStore.stringOption(options, "distance_metric", "distanceMetric", "cosine")
                ),
                InMemoryVectorStore.indexType(options)
        ));
    }
}
