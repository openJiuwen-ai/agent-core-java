/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import java.util.List;
import java.util.Map;

/**
 * Embedding model abstraction.
 */
public interface Embedding {

    List<Float> embedQuery(String text);

    default List<Float> embedQuery(String text, Map<String, Object> options) {
        return embedQuery(text);
    }

    List<List<Float>> embedDocuments(List<?> texts, Integer batchSize);

    default List<List<Float>> embedDocuments(List<?> texts, Integer batchSize, Map<String, Object> options) {
        return embedDocuments(texts, batchSize);
    }

    int getDimension();

    default int getMaxBatchSize() {
        return 256;
    }
}
