/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Input schema for memory retrieval.
 *
 * <p>Mirrors Python's {@code MemoryRetrievalInput} in
 * {@code openjiuwen/core/workflow/components/resource/memory_retrieval_comp.py}.</p>
 */
public class MemoryRetrievalInput {

    @JsonProperty("query")
    private String query;

    @JsonProperty("top_k")
    private int topK = 5;

    private Map<String, Object> extraFields = new LinkedHashMap<>();

    public MemoryRetrievalInput() {
    }

    public MemoryRetrievalInput(String query, int topK, Map<String, Object> extraFields) {
        this.query = query;
        this.topK = topK;
        setExtraFields(extraFields);
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public Map<String, Object> getExtraFields() {
        return new LinkedHashMap<>(extraFields);
    }

    public void setExtraFields(Map<String, Object> extraFields) {
        this.extraFields = extraFields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraFields);
    }
}
