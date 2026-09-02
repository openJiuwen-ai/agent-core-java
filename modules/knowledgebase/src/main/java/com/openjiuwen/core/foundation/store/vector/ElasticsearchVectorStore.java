/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import java.util.Map;

/**
 * Foundation-store Elasticsearch adapter.
 * <p>
 * Delegates to the retrieval ElasticsearchVectorStore REST backend.
 * </p>
 * 
 * @since 0.1.7
 */
public class ElasticsearchVectorStore extends AbstractRetrievalVectorStoreAdapter {
    /**
     * ElasticsearchVectorStore.
     * 
     * @param options options
     * @since 0.1.7
     */
    public ElasticsearchVectorStore(Map<String, Object> options) {
        super(new com.openjiuwen.core.retrieval.vector_store.ElasticsearchVectorStore(config(options),
                InMemoryVectorStore.indexType(options)));
    }

    /**
     * config.
     * 
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private static VectorStoreConfig config(Map<String, Object> options) {
        return new VectorStoreConfig("elasticsearch",
                InMemoryVectorStore.stringOption(options, "database_name", "databaseName", ""),
                InMemoryVectorStore.stringOption(options, "collection_name", "collectionName", "default_collection"),
                InMemoryVectorStore.stringOption(options, "distance_metric", "distanceMetric", "cosine"));
    }
}
