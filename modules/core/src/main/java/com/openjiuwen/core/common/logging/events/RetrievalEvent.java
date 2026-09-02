/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * Retrieval related event.
 * 
 * @since 0.1.7
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RetrievalEvent extends BaseLogEvent {
    private String retrievalType;
    private String query;
    private Integer topK;
    private List<Map<String, Object>> retrievedDocs;
    private Double retrievalScore;
    private Double latencyMs;
    private String knowledgeBaseId;

    /**
     * RetrievalEvent.
     * 
     * @since 0.1.7
     */
    public RetrievalEvent() {
        super();
        setModuleType(ModuleType.RETRIEVAL);
    }

    /**
     * addFieldsToMap.
     * 
     * @param map map
     * @since 0.1.7
     */
    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "retrieval_type", retrievalType);
        putIfNotNull(map, "query", query);
        putIfNotNull(map, "top_k", topK);
        putIfNotNull(map, "retrieved_docs", retrievedDocs);
        putIfNotNull(map, "retrieval_score", retrievalScore);
        putIfNotNull(map, "latency_ms", latencyMs);
        putIfNotNull(map, "knowledge_base_id", knowledgeBaseId);
    }
}
