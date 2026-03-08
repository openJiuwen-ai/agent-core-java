/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.reranker;

import com.openjiuwen.core.retrieval.common.RetrievalResult;

import java.util.List;

/**
 * Reranker abstraction.
 */
public interface Reranker {

    List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK);
}
