/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector.provider;

import com.openjiuwen.core.foundation.store.vector.ElasticsearchVectorStore;
import com.openjiuwen.spi.store.vector.BaseVectorStore;
import com.openjiuwen.spi.store.vector.VectorStoreProvider;

import java.util.Map;

/**
 * Built-in vector store provider for Elasticsearch.
 * <p>
 * Creates vector store instances backed by Elasticsearch, leveraging its
 * dense vector field type for similarity search and RAG retrieval pipelines.
 *
 * @since 0.1.12
 * @see VectorStoreProvider
 * @see com.openjiuwen.core.foundation.store.vector.ElasticsearchVectorStore
 */
public final class ElasticsearchVectorStoreProvider implements VectorStoreProvider {
    /**
     * Returns the Elasticsearch vector store type name.
     *
     * @return the type name "elasticsearch"
     */
    @Override
    public String typeName() {
        return "elasticsearch";
    }

    /**
     * Creates a new Elasticsearch vector store instance.
     *
     * @param conf the configuration map for Elasticsearch connection
     * @return a new ElasticsearchVectorStore instance
     */
    @Override
    public BaseVectorStore create(Map<String, Object> conf) {
        return new ElasticsearchVectorStore(conf);
    }
}
