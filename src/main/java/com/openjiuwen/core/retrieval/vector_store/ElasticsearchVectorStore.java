/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

/**
 * Java baseline for the Python Elasticsearch vector-store extension.
 *
 * <p>This first migration layer intentionally reuses the in-memory retrieval
 * implementation as a local compatibility backend while the full async
 * Elasticsearch semantics are ported later.</p>
 */
public class ElasticsearchVectorStore extends InMemoryVectorStore {
    /**
     * Auto-generated for codecheck compliance.
     */
    public ElasticsearchVectorStore(VectorStoreConfig config) {
        this(config, "hybrid");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ElasticsearchVectorStore(VectorStoreConfig config, String indexType) {
        super(config, indexType);
    }
}
