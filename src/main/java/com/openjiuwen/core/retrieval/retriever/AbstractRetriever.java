/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.retrieval.common.RetrievalResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Common retriever defaults.
 */
public abstract class AbstractRetriever implements Retriever {

    @Override
    public List<List<RetrievalResult>> batchRetrieve(List<String> queries,
                                                     int topK,
                                                     String mode,
                                                     Map<String, Object> options) {
        List<List<RetrievalResult>> results = new ArrayList<>();
        if (queries == null) {
            return results;
        }
        for (String query : queries) {
            results.add(retrieve(query, topK, null, mode, options));
        }
        return results;
    }
}
