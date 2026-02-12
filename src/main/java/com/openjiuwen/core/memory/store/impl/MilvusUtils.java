/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.store.impl;

import com.openjiuwen.core.retrieval.common.SearchResult;
import io.milvus.response.SearchResultsWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for Milvus operations.
 * Converts Milvus-specific results to common SearchResult format.
 */
public final class MilvusUtils {

    /**
     * Memory ID length constant.
     */
    public static final int MEMORY_ID_LENGTH = 36;

    /**
     * Store type identifier for error messages.
     */
    public static final String STORE_TYPE = "milvus vector store";

    private MilvusUtils() {
        // Utility class, no instantiation
    }

    /**
     * Convert Milvus search results to list of SearchResult.
     *
     * @param results list of IDScore from Milvus search
     * @return list of SearchResult
     */
    public static List<SearchResult> convertMilvusResult(List<SearchResultsWrapper.IDScore> results) {
        List<SearchResult> finalResults = new ArrayList<>();
        
        for (SearchResultsWrapper.IDScore hit : results) {
            String memoryId = hit.getStrID();
            float distance = hit.getScore();
            
            finalResults.add(SearchResult.builder()
                    .id(memoryId)
                    .score(distance)
                    .text("")
                    .build());
        }
        
        return finalResults;
    }
}

