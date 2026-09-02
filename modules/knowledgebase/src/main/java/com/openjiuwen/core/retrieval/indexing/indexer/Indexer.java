/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.embedding.Embedding;

import java.util.List;
import java.util.Map;

/**
 * Index manager abstraction.
 * 
 * @since 0.1.7
 */
public interface Indexer extends IndexBackendConfig, AutoCloseable {
    /**
     * buildIndex.
     * 
     * @param chunks chunks
     * @param config config
     * @param embedModel embedModel
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel, Map<String, Object> options);

    /**
     * updateIndex.
     * 
     * @param chunks chunks
     * @param docId docId
     * @param config config
     * @param embedModel embedModel
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config, Embedding embedModel,
            Map<String, Object> options);

    /**
     * deleteIndex.
     * 
     * @param docId docId
     * @param indexName indexName
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    boolean deleteIndex(String docId, String indexName, Map<String, Object> options);

    /**
     * indexExists.
     * 
     * @param indexName indexName
     * @return the result
     * @since 0.1.7
     */
    boolean indexExists(String indexName);

    /**
     * getIndexInfo.
     * 
     * @param indexName indexName
     * @return the result
     * @since 0.1.7
     */
    Map<String, Object> getIndexInfo(String indexName);

    @Override
    /**
     * close.
     * 
     * @since 0.1.7
     */
    default void close() {
    }
}
