/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.embedding.Embedding;

import java.util.List;
import java.util.Map;

/**
 * Index manager abstraction.
 */
public interface Indexer extends IndexBackendConfig, AutoCloseable {

    boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel, Map<String, Object> options);

    boolean updateIndex(List<TextChunk> chunks,
                        String docId,
                        IndexConfig config,
                        Embedding embedModel,
                        Map<String, Object> options);

    boolean deleteIndex(String docId, String indexName, Map<String, Object> options);

    boolean indexExists(String indexName);

    Map<String, Object> getIndexInfo(String indexName);

    @Override
    default void close() {
    }
}
