/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import java.util.Map;

/**
 * Foundation-store Milvus adapter.
 */
public class MilvusVectorStore extends AbstractRetrievalVectorStoreAdapter {

    public MilvusVectorStore(Map<String, Object> options) {
        super(new com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore(
                new VectorStoreConfig(
                        "milvus",
                        InMemoryVectorStore.stringOption(options, "database_name", "databaseName", "default"),
                        InMemoryVectorStore.stringOption(options, "collection_name", "collectionName", "default_collection"),
                        InMemoryVectorStore.stringOption(options, "distance_metric", "distanceMetric", "cosine")
                ),
                InMemoryVectorStore.stringOption(options, "milvus_uri", "milvusUri", ""),
                options != null && (options.containsKey("milvus_token") || options.containsKey("milvusToken"))
                        ? InMemoryVectorStore.stringOption(options, "milvus_token", "milvusToken", null)
                        : null,
                InMemoryVectorStore.indexType(options)
        ));
    }
}
