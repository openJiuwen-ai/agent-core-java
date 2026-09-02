/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.Map;

/**
 * Provider for an optional KnowledgeBase vector store implementation.
 *
 * @since 0.1.15
 */
public interface VectorStoreProvider {
    /**
     * Returns the configured store type handled by this provider.
     *
     * @return store type name
     * @since 0.1.15
     */
    String storeType();

    /**
     * Creates a vector store from the existing public configuration.
     *
     * @param config vector store configuration
     * @param indexType retrieval index type
     * @param options creation options
     * @return vector store instance
     * @since 0.1.15
     */
    VectorStore create(VectorStoreConfig config, String indexType, Map<String, Object> options);
}
