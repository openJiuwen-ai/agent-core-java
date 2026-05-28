/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.base_reranker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reranker model abstract base class.
 * <p>
 * Mirrors Python's {@code Reranker} ABC from
 * <code>foundation/store/base_reranker.py</code>.
 *
 * <p>Provides a unified interface for reranker models.
 * Subclasses must implement both async and sync rerank methods.
 */
public abstract class Reranker {

    /**
     * Rerank documents and return a mapping from document to relevance score.
     *
     * @param query    the query string
     * @param docs     list of documents (strings or Document objects) to rerank
     * @param instruct whether to provide instruction; pass a string for custom instruction
     * @param kwargs   extra arguments
     * @return mapping from document text to relevance score
     */
    public abstract LinkedHashMap<String, Float> rerank(String query, List<Object> docs, Object instruct, Map<String, Object> kwargs);

    /**
     * Build request headers.
     */
    protected Map<String, String> requestHeaders(Map<String, Object> kwargs) {
        return new LinkedHashMap<>();
    }

    /**
     * Build request parameters.
     */
    protected Map<String, Object> requestParams(Map<String, Object> kwargs) {
        return new LinkedHashMap<>();
    }

    /**
     * Parse reranker response into document-score mapping.
     */
    protected LinkedHashMap<String, Float> parseResponse(Map<String, Object> responseData, List<Object> docs) {
        return new LinkedHashMap<>();
    }
}
