/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import java.util.Map;

/**
 * Foundation-store Chroma adapter.
 */
public class ChromaVectorStore extends AbstractRetrievalVectorStoreAdapter {

    public ChromaVectorStore(Map<String, Object> options) {
        super(new com.openjiuwen.core.retrieval.vector_store.ChromaVectorStore(
                new VectorStoreConfig(
                        "chroma",
                        InMemoryVectorStore.stringOption(options, "database_name", "databaseName", "default"),
                        InMemoryVectorStore.stringOption(options, "collection_name", "collectionName", "default_collection"),
                        InMemoryVectorStore.stringOption(options, "distance_metric", "distanceMetric", "cosine")
                ),
                InMemoryVectorStore.indexType(options)
        ));
    }
}
