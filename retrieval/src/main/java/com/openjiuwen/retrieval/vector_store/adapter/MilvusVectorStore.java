/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.retrieval.vector_store.adapter;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import java.util.Map;

/**
 * Foundation-store Milvus adapter.
 *
 * @since 0.1.7
 */
public class MilvusVectorStore extends AbstractRetrievalVectorStoreAdapter {
    /**
     * MilvusVectorStore.
     *
     * @param options options
     * @since 0.1.7
     */
    public MilvusVectorStore(Map<String, Object> options) {
        super(new com.openjiuwen.retrieval.vector_store.MilvusVectorStore(
                new VectorStoreConfig("milvus",
                        AdapterOptions.stringOption(options, "database_name", "databaseName", "default"),
                        AdapterOptions.stringOption(options, "collection_name", "collectionName",
                                "default_collection"),
                        AdapterOptions.stringOption(options, "distance_metric", "distanceMetric", "cosine")),
                AdapterOptions.stringOption(options, "milvus_uri", "milvusUri", ""),
                options != null && (options.containsKey("milvus_token") || options.containsKey("milvusToken"))
                        ? AdapterOptions.stringOption(options, "milvus_token", "milvusToken", null)
                        : null,
                AdapterOptions.indexType(options)));
    }

    /**
     * Wraps an existing Milvus retrieval store as a foundation vector store.
     *
     * @param delegate retrieval store to wrap
     * @since 0.1.7
     */
    public MilvusVectorStore(com.openjiuwen.retrieval.vector_store.MilvusVectorStore delegate) {
        super(delegate);
    }
}
