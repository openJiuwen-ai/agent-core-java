/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import java.util.List;
import java.util.Map;

/**
 * Embedding model abstraction.
 * 
 * @since 0.1.7
 */
public interface Embedding {
    /**
     * embedQuery.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    List<Float> embedQuery(String text);

    /**
     * embedQuery.
     * 
     * @param text text
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    default List<Float> embedQuery(String text, Map<String, Object> options) {
        return embedQuery(text);
    }

    /**
     * embedDocuments.
     * 
     * @param texts texts
     * @param batchSize batchSize
     * @return the result
     * @since 0.1.7
     */
    List<List<Float>> embedDocuments(List<?> texts, Integer batchSize);

    /**
     * embedDocuments.
     * 
     * @param texts texts
     * @param batchSize batchSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    default List<List<Float>> embedDocuments(List<?> texts, Integer batchSize, Map<String, Object> options) {
        return embedDocuments(texts, batchSize);
    }

    /**
     * getDimension.
     * 
     * @return the result
     * @since 0.1.7
     */
    int getDimension();

    /**
     * getMaxBatchSize.
     * 
     * @return the result
     * @since 0.1.7
     */
    default int getMaxBatchSize() {
        return 256;
    }
}
