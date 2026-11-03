/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.base_reranker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Reranker model abstract base class.
 * <p>
 * Mirrors Python's {@code Reranker} in
 * {@code openjiuwen/core/foundation/store/base_reranker.py}.
 */
public abstract class Reranker {

    /**
     * Rerank documents and return a mapping from document to relevance score.
     *
     * @param query query string
     * @param doc list of strings or {@link Document} values to rerank
     * @param instruct whether to provide instruction to reranker, or a custom instruction string
     * @param kwargs extra arguments
     * @return async mapping from document to relevance score
     */
    public abstract CompletableFuture<Map<String, Double>> rerank(
            String query,
            List<Object> doc,
            Object instruct,
            Map<String, Object> kwargs
    );

    /**
     * Synchronous rerank variant.
     *
     * @param query query string
     * @param doc list of strings or {@link Document} values to rerank
     * @param instruct whether to provide instruction to reranker, or a custom instruction string
     * @param kwargs extra arguments
     * @return mapping from document to relevance score
     */
    public abstract Map<String, Double> rerankSync(
            String query,
            List<Object> doc,
            Object instruct,
            Map<String, Object> kwargs
    );

    protected Map<String, String> requestHeaders(Map<String, Object> kwargs) {
        return new LinkedHashMap<>();
    }

    protected Map<String, Object> requestParams(Map<String, Object> kwargs) {
        return new LinkedHashMap<>();
    }

    protected Map<String, Double> parseResponse(Map<String, Object> responseData, List<Object> doc) {
        return new LinkedHashMap<>();
    }
}
