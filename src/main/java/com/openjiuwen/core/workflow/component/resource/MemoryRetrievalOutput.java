/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.memory.MemResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Output schema for memory retrieval.
 *
 * <p>Mirrors Python's {@code MemoryRetrievalOutput} in
 * {@code openjiuwen/core/workflow/components/resource/memory_retrieval_comp.py}.</p>
 */
public class MemoryRetrievalOutput {

    @JsonProperty("fragment_memory_results")
    private List<MemResult> fragmentMemoryResults = new ArrayList<>();

    @JsonProperty("summary_results")
    private List<MemResult> summaryResults = new ArrayList<>();

    public MemoryRetrievalOutput() {
    }

    public MemoryRetrievalOutput(List<MemResult> fragmentMemoryResults, List<MemResult> summaryResults) {
        setFragmentMemoryResults(fragmentMemoryResults);
        setSummaryResults(summaryResults);
    }

    public List<MemResult> getFragmentMemoryResults() {
        return new ArrayList<>(fragmentMemoryResults);
    }

    public void setFragmentMemoryResults(List<MemResult> fragmentMemoryResults) {
        this.fragmentMemoryResults = fragmentMemoryResults == null
                ? new ArrayList<>()
                : new ArrayList<>(fragmentMemoryResults);
    }

    public List<MemResult> getSummaryResults() {
        return new ArrayList<>(summaryResults);
    }

    public void setSummaryResults(List<MemResult> summaryResults) {
        this.summaryResults = summaryResults == null ? new ArrayList<>() : new ArrayList<>(summaryResults);
    }

    /**
     * Convert to Python's {@code model_dump()} dictionary shape.
     *
     * @return plain output map with Python field names
     */
    public Map<String, Object> toMap() {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("fragment_memory_results", new ArrayList<>(fragmentMemoryResults));
        output.put("summary_results", new ArrayList<>(summaryResults));
        return output;
    }
}
