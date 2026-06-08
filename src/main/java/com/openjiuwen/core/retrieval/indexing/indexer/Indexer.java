/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.embedding.Embedding;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code Indexer} in
 * {@code openjiuwen/core/retrieval/indexing/indexer/base.py}.
 */
public abstract class Indexer {

    public abstract CompletableFuture<Boolean> buildIndex(
            List<TextChunk> chunks,
            IndexConfig config,
            Embedding embedModel,
            Map<String, Object> kwargs
    );

    public abstract CompletableFuture<Boolean> updateIndex(
            List<TextChunk> chunks,
            String docId,
            IndexConfig config,
            Embedding embedModel,
            Map<String, Object> kwargs
    );

    public abstract CompletableFuture<Boolean> deleteIndex(
            String docId,
            String indexName,
            Map<String, Object> kwargs
    );

    public abstract CompletableFuture<Boolean> indexExists(String indexName);

    public abstract CompletableFuture<Map<String, Object>> getIndexInfo(String indexName);
}
